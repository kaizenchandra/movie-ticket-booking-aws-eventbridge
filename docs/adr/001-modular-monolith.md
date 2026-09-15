# ADR 001 — Modular monolith with explicit blocking transaction boundary

Accepted. Catalog, scheduling, inventory, booking, payments and notifications share a PostgreSQL database and JVM
deployment. Seat ownership and booking confirmation require a single short transaction; splitting services would
introduce a distributed seat-allocation protocol without a demonstrated scaling benefit. Framework-independent
application ports and domain models remain testable without Spring. ArchUnit enforces inward core dependencies and
entity isolation. Package modules are compiled in one Maven artifact; a future extraction must first move the
corresponding database ownership boundary.

WebFlux is retained for the required HTTP/SSE transport. JPA is retained for imperative persistence. Complete
transactions run on eight bounded threads rather than Netty event loops. This limits throughput to database capacity and
avoids pretending JPA is reactive.

The initial show-wide exclusive lock is superseded by ADR 003. Shared show metadata protection, booking-row
serialization and sorted seat locks preserve race safety while allowing disjoint reservations within a show.
