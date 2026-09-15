ALTER TABLE booking
    ADD COLUMN payment_attempts integer NOT NULL DEFAULT 0;
ALTER TABLE booking
    ADD COLUMN refund_attempts integer NOT NULL DEFAULT 0;
ALTER TABLE booking
    ADD COLUMN next_payment_attempt timestamptz NOT NULL DEFAULT now();
ALTER TABLE booking
    ADD COLUMN next_refund_attempt timestamptz NOT NULL DEFAULT now();
CREATE INDEX payment_due ON booking (next_payment_attempt) WHERE payment IN ('PENDING','UNKNOWN');
CREATE INDEX refund_due ON booking (next_refund_attempt) WHERE refund='PENDING';
