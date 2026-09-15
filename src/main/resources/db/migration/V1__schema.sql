CREATE TABLE movie
(
    id               uuid PRIMARY KEY,
    title            varchar(200) NOT NULL,
    duration_minutes integer      NOT NULL CHECK (duration_minutes BETWEEN 1 AND 600)
);
CREATE TABLE cinema
(
    id   uuid PRIMARY KEY,
    name varchar(200) NOT NULL,
    city varchar(120) NOT NULL
);
CREATE TABLE screen
(
    id        uuid PRIMARY KEY,
    cinema_id uuid         NOT NULL REFERENCES cinema,
    name      varchar(100) NOT NULL,
    UNIQUE (cinema_id, name)
);
CREATE TABLE seat
(
    screen_id uuid        NOT NULL REFERENCES screen,
    label     varchar(12) NOT NULL,
    PRIMARY KEY (screen_id, label)
);
CREATE TABLE showtime
(
    id          uuid PRIMARY KEY,
    movie_id    uuid        NOT NULL REFERENCES movie,
    screen_id   uuid        NOT NULL REFERENCES screen,
    starts_at   timestamptz NOT NULL,
    ends_at     timestamptz NOT NULL,
    price_minor bigint      NOT NULL CHECK (price_minor >= 0),
    currency    varchar(3)  NOT NULL,
    CHECK (ends_at > starts_at)
);
CREATE INDEX show_browse ON showtime (starts_at, movie_id);
CREATE INDEX show_screen ON showtime (screen_id, starts_at, ends_at);
CREATE TABLE booking
(
    id          uuid PRIMARY KEY,
    show_id     uuid         NOT NULL REFERENCES showtime,
    owner       varchar(200) NOT NULL,
    seats       text         NOT NULL,
    price_minor bigint       NOT NULL CHECK (price_minor >= 0),
    currency    varchar(3)   NOT NULL,
    expires_at  timestamptz  NOT NULL,
    status      varchar(30)  NOT NULL CHECK (status IN ('HELD', 'PAYMENT_PENDING', 'CONFIRMED', 'EXPIRED', 'CANCELLED',
                                                        'FAILED')),
    hold        varchar(30)  NOT NULL CHECK (hold IN ('ACTIVE', 'CONSUMED', 'EXPIRED', 'RELEASED')),
    payment     varchar(30)  NOT NULL CHECK (payment IN ('NONE', 'PENDING', 'UNKNOWN', 'SUCCEEDED', 'FAILED')),
    refund      varchar(30)  NOT NULL CHECK (refund IN ('NONE', 'PENDING', 'SUCCEEDED')),
    version     bigint       NOT NULL,
    mode        varchar(30)  NOT NULL,
    request_key varchar(128) NOT NULL,
    fingerprint text         NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (owner, request_key),
    UNIQUE (id, show_id)
);
CREATE INDEX booking_history ON booking (owner, created_at DESC, id);
CREATE INDEX booking_expiry ON booking (expires_at, show_id) WHERE hold='ACTIVE';
CREATE INDEX payment_pending ON booking (id) WHERE payment IN ('PENDING','UNKNOWN');
CREATE INDEX refund_pending ON booking (id) WHERE refund='PENDING';
CREATE TABLE show_seat
(
    show_id    uuid        NOT NULL REFERENCES showtime,
    label      varchar(12) NOT NULL,
    booking_id uuid,
    PRIMARY KEY (show_id, label),
    FOREIGN KEY (booking_id, show_id) REFERENCES booking (id, show_id)
);
CREATE INDEX show_seat_booking ON show_seat (booking_id);
CREATE TABLE ticket
(
    id         uuid PRIMARY KEY,
    booking_id uuid        NOT NULL REFERENCES booking,
    label      varchar(12) NOT NULL,
    revoked    boolean     NOT NULL DEFAULT false,
    UNIQUE (booking_id, label)
);
CREATE TABLE outbox
(
    id                uuid PRIMARY KEY,
    aggregate_id      uuid         NOT NULL,
    aggregate_version bigint       NOT NULL,
    type              varchar(100) NOT NULL,
    payload           text         NOT NULL,
    created_at        timestamptz  NOT NULL,
    delivered_at      timestamptz,
    claim_token       uuid,
    lease_until       timestamptz,
    available_at      timestamptz  NOT NULL,
    attempts          integer      NOT NULL DEFAULT 0,
    last_error        varchar(100),
    UNIQUE (aggregate_id, aggregate_version)
);
CREATE INDEX outbox_pending ON outbox (available_at, created_at) WHERE delivered_at IS NULL;
CREATE TABLE consumer_receipt
(
    consumer     varchar(60) NOT NULL,
    event_id     uuid        NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);
CREATE TABLE notification
(
    booking_id uuid PRIMARY KEY REFERENCES booking,
    version    bigint      NOT NULL,
    status     varchar(30) NOT NULL,
    updated_at timestamptz NOT NULL
);
CREATE TABLE simulated_charge
(
    id              uuid PRIMARY KEY,
    price_minor     bigint      NOT NULL,
    currency        varchar(3)  NOT NULL,
    scenario        varchar(30) NOT NULL,
    ready_at        timestamptz NOT NULL,
    refunded        boolean     NOT NULL DEFAULT false,
    refund_attempts integer     NOT NULL DEFAULT 0
);
