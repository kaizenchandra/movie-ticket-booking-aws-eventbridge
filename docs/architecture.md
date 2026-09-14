# Architecture

```mermaid
flowchart LR
  Browser[Browser / API client] --> HTTP[WebFlux + JWT]
  HTTP --> Boundary[Bounded scheduler + transaction boundary]
  Boundary --> Core[Application ports / domain transitions]
  Core --> JPA[JPA adapters]
  JPA --> DB[(PostgreSQL)]
  DB --> Outbox[Leased outbox workers]
  Outbox --> EB[EventBridge]
  EB --> SQS[SQS notification queue]
  SQS --> Consumer[Deduplicating consumer]
  Consumer --> DB
  Payments[Durable reconciliation worker] --> Provider[Payment provider port]
  Payments --> Boundary
  DB --> Snapshots[Per-connection snapshot queries on every replica]
  Snapshots --> Browser
```

Core code has no Spring, JPA, AWS SDK or Reactor dependencies. JPA `BookingEntity` is separate from immutable domain `Booking`. Query adapters return eagerly mapped projections within transaction scope. No lazy entity is returned through HTTP. `Transactions` is an application port; `JpaTransactions` is the explicit Spring TransactionTemplate adapter. `BlockingBoundary.call` schedules the entire transaction on `jpa-*`. A runtime guard rejects JPA transaction entry from Reactor nonblocking threads. Security identity is passed explicitly as a subject string; Reactor automatic context propagation carries registered Micrometer tracing context across the scheduler. No request handler uses subscribe(), block(), or a reactive thread switch inside a transaction.

Admission: at most 128 ordinary requests and 100 SSE streams per replica; 200 requests/second per replica. Bounded scheduler saturation returns 503. The eight-thread scheduler has at most 16 pending tasks per backing worker. Workers have their own four platform threads and one task per loop. Hikari has 16 connections, leaving headroom over eight HTTP and four worker operations. Each replica adds this connection demand; a four-replica rolling deployment may temporarily have eight tasks, so review RDS max_connections and scale limits before increasing DesiredCount. CPU, lock waits, database I/O and snapshot traffic bound throughput; this is not a fully nonblocking system. Spring MVC/virtual threads would simplify thread management; R2DBC would require replacing the persistence stack. Neither substitution was made.

## Locking and atomicity

Reservation lock order is PostgreSQL transaction-scoped advisory lock over `(owner,key)`, then `showtime FOR UPDATE`, then booking/seat writes. A hash collision only serializes unrelated keys. Other seat-changing use cases lock the show first and reload the booking after acquiring it. The persistence context is cleared after lock acquisition so data loaded before waiting cannot hide a concurrent cancellation or expiry. Screen scheduling locks the screen row and checks interval overlap before inserting a show. All operations act on one show at a time; no multi-show lock inversion exists.

`show_seat(show_id,label)` is the single ownership slot, with a foreign key tying its booking to the same show. Claim updates require `booking_id IS NULL`. A reservation updates all selected seats in one database transaction; any failed condition rolls it all back. Database constraints reinforce unique request keys and tickets. Reads may advertise expired seats before physical cleanup; reserve first expires those holds while holding the show lock. Commit remains authoritative.

Each change persists its outbox event with the booking transaction. Cancellation revokes existing tickets; duplicate confirmation inserts with `ON CONFLICT DO NOTHING`. A late success can only confirm an ACTIVE hold strictly before its expiry. All other successful payments schedule a full refund without allocating any seat. Out-of-order failure after success is ignored; success after previously reported failure is treated as a charge requiring compensation. Duplicate refund acknowledgement is a no-op.

## State machines

| Machine | Allowed transitions |
|---|---|
| Booking | HELD → PAYMENT_PENDING, EXPIRED, CANCELLED; PAYMENT_PENDING → CONFIRMED, FAILED, EXPIRED, CANCELLED; CONFIRMED → CANCELLED; FAILED/EXPIRED → CANCELLED before show; terminal cancellations stay CANCELLED |
| Hold | ACTIVE → CONSUMED on valid success; ACTIVE → EXPIRED on elapsed deadline; ACTIVE/CONSUMED → RELEASED on cancellation; ACTIVE → RELEASED on failure; expired/released never become ACTIVE |
| Payment | NONE → PENDING; PENDING → UNKNOWN/SUCCEEDED/FAILED; UNKNOWN → SUCCEEDED/FAILED; FAILED → SUCCEEDED only with compensation; SUCCEEDED ignores later failures/duplicates |
| Refund | NONE → PENDING for a successful invalid/cancelled booking; PENDING → SUCCEEDED after provider acknowledgement; failed/uncertain attempts remain PENDING and are retried |

Booking and hold are created together. Expiry/cancellation does not discard pending payment state: reconciliation continues because the provider may have charged. Refund has no terminal FAILED state: losing an obligation because a retry budget elapsed would be incorrect. Exponential retry delays persist, cap at 300 seconds, and require operational intervention for sustained failures. Automatic reconciliation is indefinite while the obligation remains; per-call SDK retries are bounded. No new payment or refund idempotency key is generated during recovery.

Restart-safe expiry queries indexed expired ACTIVE rows every second. A missed expiry job is harmless to safety; every reservation and settlement rechecks time. The injected UTC clock enables boundary tests. Production hosts require reliable NTP; database advisory availability uses database UTC time while domain decisions use the injected system clock.

## Events and notifications

Every booking version emits `BookingChanged.v1`, source `cinema.booking`. Detail contains stable eventId, schemaVersion=1, type, aggregateId/version, UTC occurredAt, correlationId (booking), causationId (previous outbox event, or initial booking command ID), status, showId. It contains no customer identity or payment details. Schema: `events/booking-changed-v1.schema.json`. Notification routing uses exact source and detail type. The consumer materializes an in-app notification projection, not email/SMS delivery. Email/SMS would need its own outbox and provider idempotency strategy.

Claims use short transactions with `FOR UPDATE SKIP LOCKED`, a 60-second lease and random fencing token. Network calls happen after commit. Each EventBridge result is checked independently. Retry delay increases exponentially to 300 seconds. A publisher dying after publication but before marking will republish the same event ID after lease expiry. Marking requires the current claim token. At-least-once delivery is intentional; there is no exactly-once claim.

SQS acknowledgements follow the successful side-effect transaction. The receipt `(consumer,eventId)` and monotonic notification upsert commit together. Delayed lower aggregate versions cannot regress state. Unsupported envelopes/schemas fail and enter the consumer DLQ after five receives. Visibility is 60 seconds; processing transactions are limited to five seconds. Failed receives use bounded exponential visibility backoff. Receipt deletion is not tied to a connection-local memory cache.

EventBridge target DLQ handles delivery to SQS (permissions/unavailable target/retry exhaustion). SQS consumer DLQ handles accepted queue messages that repeatedly fail processing. These are distinct queues and recovery procedures. Durable payment reconciliation is driven by database obligations rather than consumer delivery order; EventBridge availability is not on the charge/seat correctness path.

## Live behavior

SSE sends immediately and every two seconds, each event a complete snapshot and heartbeat comment. Replicas query the same committed database, so every connected client refreshes independently of which worker consumes an SQS message. No additional broadcast service is needed at this deliberately bounded scale. Last-Event-ID is advisory: any reconnection starts from a fresh snapshot. UUID snapshot IDs are opaque, not resumable cursors. Slow connections keep only the latest pending snapshot, drop excess interval ticks, and close within five minutes or JWT expiry. Netty write pressure provides backpressure; the 100-stream admission cap bounds query load. A disconnected client resubscribes with a valid JWT and discards stale local availability.
