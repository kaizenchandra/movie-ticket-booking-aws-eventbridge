# ADR 002 — At-least-once events, durable reconciliation, snapshot SSE

Accepted. Business state and outbox share a transaction. EventBridge routes to SQS for durable consumption, with separate routing and processing DLQs. Leases/fencing support multiple publishers; stable IDs and database deduplication tolerate publication crash windows. No distributed exactly-once assumption is made.

Payments are an external side effect. Durable booking payment/refund state is reconciled outside booking transactions using provider idempotency keys. An unknown response must never trigger a second independent charge. Late success for an invalid hold creates a refund obligation.

For this bounded demo, every SSE subscriber reads a full snapshot from PostgreSQL every two seconds. This reaches clients on all replicas and naturally recovers from missed messages. The tradeoff is database load proportional to connected clients; per-replica stream limits cap it. A high-scale evolution could add Redis Streams or per-replica fanout subscriptions, but a single competing SQS queue would not broadcast. Adding that infrastructure now would increase operational scope without evidence it is needed.
