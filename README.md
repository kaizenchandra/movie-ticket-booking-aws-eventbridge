# Cinema booking backend

Java 21 / Spring Boot 4.1.1 modular monolith. WebFlux HTTP and SSE, imperative transactional JPA/Hibernate, PostgreSQL/Flyway, AWS SDK v2, outbox → EventBridge → SQS → database-deduplicated notification projection. A local payment simulator completes the booking and refund flows. This is an engineering demonstration with explicit deployment prerequisites, not a production certification.

## Quick start

Requires Java 21, Docker Compose v2, Python 3, OpenSSL, and a **LocalStack auth token with an assigned license** that includes EventBridge and SQS. Current LocalStack images require authentication; CI also needs a suitable CI token. No AWS account is needed for local operation.

```sh
./scripts/local-setup.sh
# Set LOCALSTACK_AUTH_TOKEN in your shell using your LocalStack account; do not commit it.
docker compose up --build -d --wait
./scripts/smoke.sh
./scripts/messaging-smoke.py
```

Open http://localhost:8080. Sign in as `alice` or `bob` with the random `LOCAL_CUSTOMER_PASSWORD` from your local `.env`. `admin` uses `LOCAL_ADMIN_PASSWORD`. The local token endpoint exists only in the `local` profile; its RSA keys are generated into ignored `.local/`. It is a deterministic development identity service, not an OIDC identity provider. Production validates external OIDC JWTs and has no token minting endpoint. The browser holds tokens in memory, sends Authorization headers, and refreshes seat snapshots over authenticated fetch streaming.

The seed show starts seven days after the first local migration. Once it has passed, create a new show through the admin API. Seed migrations are local-profile only. Do not reset production databases to refresh sample data.

```sh
./mvnw verify                    # unit, architecture, real PostgreSQL/API integration tests; Docker required
./mvnw test                      # unit and architecture tests only
./mvnw -DskipTests package
# Host Java alternative after starting Compose infrastructure:
set -a; . ./.env; set +a
export SPRING_PROFILES_ACTIVE=local AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test
export AWS_ENDPOINT=http://localhost:4566
java -jar target/aws-eventbridge-demo-1.0.0-SNAPSHOT.jar
```

Java 21 must be selected explicitly if your system defaults to another JDK. On macOS: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`. Maven Wrapper 3.9.16 is retained from the supplied starter. `./mvnw dependency:tree` gives the resolved Boot-managed dependency versions. `docs/verification.md` records exactly which checks were executed.

For database/HTTP-only diagnosis without an AWS emulator, set `WORKERS_AWS_ENABLED=false` when running the host JVM. Payments, refunds, expiry, and snapshots still run; outbox records accumulate. This is an explicit degraded test mode, not a replacement for the required EventBridge/SQS path. Full AWS verification requires the licensed LocalStack service.

## API and examples

The complete static OpenAPI 3.1 contract is served at `/openapi.yaml` and stored in `src/main/resources/static/openapi.yaml` (JSON syntax is valid YAML). See `docs/api-examples.md` for authenticated requests and callback signatures. APIs return snapshots with server-side price in minor units and ISO currency. At most eight distinct seats can be held in one request.

| Action | Endpoint |
|---|---|
| Discovery | `GET /api/movies`, `/api/cinemas`, `/api/shows` |
| Seat snapshot | `GET /api/shows/{id}/seats` |
| Hold + booking | `POST /api/bookings`, `Idempotency-Key` required |
| Initiate charge | `POST /api/bookings/{id}/payment` |
| Cancel/refund | `POST /api/bookings/{id}/cancel` |
| History/details/tickets | `GET /api/bookings`, `/{id}`, `/{id}/tickets` |
| Admin | `POST /api/admin/movies`, `/cinemas`, `/screens`, `/shows`; PUT catalog resources; DELETE unused show |
| In-app notification inbox | `GET /api/notifications` |
| Live snapshots | `GET /api/live/shows/{id}`, `/api/live/bookings` |

A reservation creates the booking and hold together. Retrying a reservation key returns the current same booking, not a historical response byte-for-byte. Reusing the key for a different show or sorted seat set returns `409 IDEMPOTENCY_CONFLICT`. Payment initiation uses the booking UUID as its fixed provider idempotency key; a second payment mode for that booking is rejected. Cancellation is idempotent. A failed payment cannot be retried as a new charge on the same booking: create a new reservation.

Simulator scenarios: `SUCCESS`, `FAILURE`, `DELAY` (unknown until 360 seconds later, deliberately longer than the default hold), `DUPLICATE` (duplicate successful settlement), `REFUND_RETRY` (first refund attempt defers, subsequent attempt succeeds). No card data is accepted or stored. All simulator provider state is persisted independently from booking transactions. Timeout/unknown charge outcomes remain reconcilable even after cancellation/expiry.

## Architecture and constraints

See [architecture and state machines](docs/architecture.md), [decisions](docs/adr/001-modular-monolith.md), [event contract](docs/events/booking-changed-v1.schema.json), and [operations](docs/operations.md).

Package modules: `catalog`, `scheduling`, `inventory`, `booking`, `payments`, `notifications`; each owns appropriate domain/application/adapter code. `shared` contains money/errors and the transaction port; `bootstrap` wires infrastructure and runtime boundaries. The build stays one Maven artifact, so the architecture tests enforce inward dependency direction. Scheduling, inventory, and ticket query ports have distinct persistence adapters. The booking persistence adapter owns coordinated booking/seat writes because those invariants must commit atomically. PostgreSQL foreign keys intentionally cross module schemas in this monolith.

A show-row database lock serializes reservations, expiry, settlement, and cancellation for a show. Different shows proceed independently. This makes multi-seat atomicity and races auditable but limits a hot show's throughput. JPA is blocking: eight bounded scheduler threads handle complete transactions, with 16 queued tasks per backing thread; four sequential worker loops share a 16-connection pool. Transactions have five-second bounds, database lock waits two seconds, connection acquisition two seconds, and HTTP use cases eight seconds. A timed-out HTTP request may have committed; retry the same key. Limits are per replica and require a fleet-wide database connection budget.

Full snapshots every two seconds provide cross-replica SSE consistency without extra broadcast infrastructure. They are advisory and may skip intermediate transitions. Each snapshot carries a heartbeat, reconnect delay and opaque ID; Last-Event-ID always causes a fresh snapshot. The booking stream covers the first 100 bookings; use paginated history for all records. Connections close by JWT expiry or five minutes, with a one-snapshot buffer and 100-stream admission cap per replica. This polling design increases database reads with connected clients.

Policy assumptions: UTC instants; one currency and uniform price per show; full cancellation refund strictly before showtime; no fees, discounts, split tenders, tax calculation, or ticket transfer. Held or confirmed seats are released on cancellation. Historical bookings prevent show deletion; sold showtimes and seat layouts are immutable. Edit unused showtimes by delete/recreate. A production box-office admission/check-in service is outside this backend's scope; returned ticket IDs and revocation flags demonstrate ticket generation.

## Configuration

| Variable | Meaning |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL JDBC connection |
| `BOOKING_HOLD_SECONDS` | Default 300 |
| `AWS_REGION`, `AWS_ENDPOINT`, `AWS_BUS`, `AWS_QUEUE_URL` | Real AWS or local emulator routing |
| `WORKERS_ENABLED`, `WORKERS_AWS_ENABLED` | Worker lifecycle and explicit AWS-only diagnostic switch |
| `OIDC_ISSUER`, `OIDC_JWK_SET_URI`, `OIDC_AUDIENCE` | Production RS256 signature, issuer, expiry and audience validation |
| `CORS_ORIGIN` | Exact permitted browser origin |
| `PAYMENT_CALLBACK_SECRET` | Local simulator HMAC callback verification |
| `LOCAL_KEYS`, `LOCAL_CUSTOMER_PASSWORD`, `LOCAL_ADMIN_PASSWORD` | Local-only identity setup |

Customer ownership is derived from JWT subject inside each use case. Admin role does not bypass booking ownership. Production issuer must supply a `roles` claim containing `CUSTOMER` and/or `ADMIN`. JWTs are not accepted in query strings. CSRF is disabled because APIs accept bearer headers rather than cookies; CORS allows only the configured origin, no credentials. Per-replica admission and rate limits complement the deployment's WAF IP rate rule. Local passwords and signing material must never be used outside localhost development.

## Deployment and remaining prerequisites

`infra/aws/stack.json` prepares a CloudFormation deployment with private multi-AZ Fargate/RDS networking, NAT egress, HTTPS ALB, WAF, Secrets Manager integration, EventBridge/SQS policies, encrypted queues/storage, CloudWatch logs and DLQ alarms. **No infrastructure has been provisioned.** Default desired task count is zero; starting even this infrastructure incurs charges. Review region/version availability, cost, networking and `docs/operations.md` before any separately authorized deployment.

A real payment adapter and authenticated provider callback implementation are external prerequisites. Production deliberately fails startup without a `PaymentProvider` bean; it cannot silently use the simulator. Also required: external OIDC issuer, ECR image, ACM certificate/DNS, provider secret, alarm subscription, tracing/Prometheus collector integration, database least-privilege runtime/migration users, region-specific disaster-recovery drills, dependency/security scan review, financial reconciliation operations, and production-scale performance qualification. Supplied infrastructure and tests do not establish PCI compliance or production readiness.
