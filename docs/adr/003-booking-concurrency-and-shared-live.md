# ADR 003 — Seat-level concurrency and shared live snapshots

The show-wide exclusive lock unnecessarily serialized independent seats, and per-client polling repeated identical
reads. Keep the required JPA/WebFlux stack and PostgreSQL authority while narrowing contention.

Use shared show locks for administration safety, booking locks for state transitions, and sorted inventory locks for
reservations. Discover and lock existing owners before locking inventory; never chase a newly discovered owner after
taking inventory locks. Conservative conflicts are retryable with the same key. Expiry processes expired bookings only.
All changes, tickets and outbox inserts remain in one transaction.

Use a reference-counted snapshot publisher per show/user per replica. One query feeds every subscriber of that topic
every two seconds. Stop polling and evict on the final disconnect. Reconnection can first receive the cached snapshot
and then a fresh one. The database provides cross-replica convergence without adding a broadcast dependency. Separate
subject keys isolate booking data.

Blocking JDBC cannot become nonblocking by changing a scheduler. Keep whole transactions off Netty, replace per-worker
queues with a shared bounded FIFO queue, expose concurrency/queue configuration and validate the connection budget at
startup. Rejection remains HTTP 503. Virtual threads would reduce thread cost but would not remove database connection
limits; replacing JPA with R2DBC is outside the required stack.

Validation includes an uncommitted reservation on A1 while A2 completes, overlapping expired multi-seat reclaim,
existing payment/expiry/cancellation races, and 100 live subscribers receiving two snapshots from only two reads. These
deterministic checks establish reduced serialization/read amplification; they are not a new throughput benchmark.
