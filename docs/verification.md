# Verification report — updated 2026-09-15

The repository was built and exercised locally. These results do not establish production readiness. No paid AWS infrastructure was provisioned.

## Executed checks

| Check | Result |
|---|---|
| Java 21 Maven verification | **37 tests passed** on 2026-09-15, zero failures, errors or skips: 7 domain tests, 4 architecture tests, 2 shared-snapshot tests, 24 PostgreSQL/API/integration tests |
| Runnable jar | `./mvnw -DskipTests package` succeeded after the final static/configuration updates |
| Dockerfile | Multi-stage image built successfully as `cinema-verification:local` |
| Container startup | Non-root local UID, owner-only signing-key permissions, PostgreSQL migrations and database readiness succeeded |
| Booking smoke | Browse → atomic hold → payment → confirmed ticket → cancellation → asynchronous refund passed against the container |
| Process restart | A real JVM was stopped, the hold expired while no app worker was running, and a new JVM successfully re-reserved the same seat |
| Two replicas | An SSE connection on replica B observed a hold committed through replica A; reconnect after cancellation returned refreshed availability |
| Deployment contract | `cfn-lint 1.56.3` passed for `infra/aws/stack.json`; nothing deployed |
| API contract | `openapi-spec-validator 0.9.0` passed for the OpenAPI 3.1 document |
| Compose | Base and optional two-replica configurations parsed successfully |
| Scripts/browser source | Bash/Python syntax checks and `node --check` passed; browser click automation was not performed |

The test results were refreshed on 2026-09-15; container smoke, restart, replica, deployment/API validation and load results below are retained from 2026-09-14 and were not rerun for this update. The load figures predate the seat-lock and shared-snapshot changes.

The initial sandboxed attempt could not initialize Mockito instrumentation. An authorized run outside the sandbox passed all 13 unit/architecture/shared-snapshot tests, but integration startup required starting OrbStack. The subsequent `./mvnw -o failsafe:integration-test failsafe:verify` run with the OrbStack `DOCKER_HOST` passed all 24 integration tests. Earlier HTTP failures containing `Proxy key is incorrect` did not recur; no application fix was needed for those responses.

Maven ran with installed Java **21.0.12.1**. Integration tests used **Testcontainers PostgreSQL 17.6**, real Flyway migrations, Hibernate and an actual Netty HTTP server. JWT tests exercised signature-backed tokens, issuer/audience/expiry validation and authorization. AWS SDK clients were mocked in these tests: their failure/acknowledgement assertions are not a substitute for a live AWS integration run.

Evidence: [Maven summary](verification/maven-results.json), [container smoke](verification/container-smoke.log), [restart smoke](verification/restart-smoke.log), [replica smoke](verification/replica-smoke.log). Full local Maven reports remain under `target/surefire-reports` and `target/failsafe-reports`.

## Correctness coverage

- A reservation for A2 completed while an uncommitted reservation held A1; overlapping expired multi-seat owners were reclaimed without deadlock.
- One hundred subscribers shared two snapshot reads across two ticks; final disconnect evicted the snapshot, and separate user topics remained isolated.
- Twelve concurrent attempts for one seat produced one active reservation. Overlapping multi-seat attempts committed one complete set, with no partial allocation.
- Concurrent retries of one key returned the same booking; changed payloads were rejected.
- Expiry/payment and cancellation/payment races produced consistent terminal states and refund obligations.
- Failure released seats; duplicate success preserved one provider charge and one ticket per seat. Unknown delayed payments were reconciled and compensated after expiry.
- Signed callbacks rejected forgery, tolerated duplicates, and supported a subsequent cancellation/refund even when callback settlement preceded the regular payment worker.
- Consumer receipts rolled back with failed side effects; duplicate and lower-version events did not regress the notification projection.
- Outbox tests covered leases, expired-claim recovery, stale fencing tokens, publication outages and mixed successful/failed EventBridge entries. Publication/mark crash recovery retained the original event ID.
- SQS adapter tests verified acknowledgement only for a valid committed message and visibility backoff for poison input.
- Ownership and role tests covered customer booking access and notification isolation. SSE reconnect refreshed authoritative state without leaking seat-owner identifiers.
- Reactor tests verified an active JPA transaction on `jpa-*` threads and rejection of transaction entry on Reactor nonblocking threads.
- Oversized chunked request bodies returned 413 with the stable top-level `REQUEST_TOO_LARGE` code.

## Measured load

Final backend workload: one **2-CPU, 1-GiB** application container, PostgreSQL 17.6 in local OrbStack, host k6 client, 30 seconds, ten paced browsing users plus 100 reservation attempts from ten concurrent users against a newly created show. AWS workers were explicitly disabled; payment/expiry workers and database-backed snapshots remained available. No separate integration test run overlapped this final measurement.

| Measurement | Observed |
|---|---:|
| Requests / throughput | 1,506 / 49.88 requests per second |
| Business checks | 1,506 passed, 0 failed |
| Browse latency p50 / p95 / p99 | 11.27 / 27.19 / 39.99 ms |
| Reservation latency p50 / p95 / p99 | 44.55 / 93.14 / 365.36 ms |
| Reservation outcomes | 1 success, 99 expected seat conflicts |
| Rate-limited requests | 0 |
| Highest sampled app CPU / memory | 154.69% of one core / 453.2 MiB |
| Highest sampled database CPU / memory | 6.99% of one core / 55.81 MiB |

The CPU limit was two cores, so 200% is the corresponding Docker CPU ceiling. Resource figures are the highest of six samples, not continuous peak profiling. k6's HTTP-failure counter includes the 99 expected 409 conflicts; the business checks and thresholds passed.

Raw [container load data](verification/load-container.json), [resource samples](verification/container-resources.log), and [measured image/limits](verification/load-container-environment.txt) are retained. The final browser-only sign-in-header correction did not change the measured backend paths.

An earlier unpaced host run overwhelmed the 200-request/second admission gate and failed 99.71% of its business checks. Its [raw result](verification/load-overload.json) is retained as an overload observation, not successful application throughput. An earlier [paced host run](verification/load-paced.json) used different runtime conditions and is not the primary container result.

Reproduce the paced test against a running local app with `scripts/load-local.sh`. `scripts/load.sh` also accepts your own TOKEN and disposable SHOW_ID. These short development-machine measurements do not qualify production throughput, sustained memory behavior, payment-provider latency or AWS messaging capacity.

## Supplied but not executed

- **Licensed LocalStack integration:** no `LOCALSTACK_AUTH_TOKEN` was configured. The actual EventBridge → SQS route, live AWS outage behavior, poison-message DLQ transition and replay smoke remain **unverified**. Run the complete Compose stack, `scripts/smoke.sh`, then `scripts/messaging-smoke.py` with an appropriately licensed token. No alternative emulator was substituted.
- GitHub Actions execution, Trivy and Gitleaks scans were supplied but not run during this session.
- OTLP collector export/visualization, external OIDC-provider deployment, a real payment provider, AWS staging tests and disaster-recovery drills were not performed.
- CloudFormation was validated locally but neither submitted nor provisioned.

Production gates remain: implement and qualify the real provider/callback adapter; configure OIDC, TLS/DNS and monitoring; split migration/runtime database privileges; review dependency and image scan results; provision only after separate authorization; and execute the AWS, financial reconciliation, sustained-load and recovery exercises described in `operations.md`.
