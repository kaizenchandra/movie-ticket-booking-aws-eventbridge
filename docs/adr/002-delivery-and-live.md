# ADR 002 — At-least-once events, durable reconciliation, snapshot SSE

Accepted. Business state and outbox share a transaction. EventBridge routes to SQS for durable consumption, with separate routing and processing DLQs. Leases/fencing support multiple publishers; stable IDs and database deduplication tolerate publication crash windows. No distributed exactly-once assumption is made.

Payments are an external side effect. Durable booking payment/refund state is reconciled outside booking transactions using provider idempotency keys. An unknown response must never trigger a second independent charge. Late success for an invalid hold creates a refund obligation.

Per-subscriber snapshot polling is superseded by ADR 003: reference-counted per-topic fanout shares each query within a replica. Each replica still reads PostgreSQL independently, so a competing SQS queue is never treated as broadcast. No additional infrastructure is needed.
