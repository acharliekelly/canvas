# ADR 0003: Local MVP Deployment Topology

**Status:** Accepted

**Decision date:** 2026-07-22

**Recorded date:** 2026-07-22

## Context

The local MVP must demonstrate the complete editorial workflow without a cloud account, GPU, paid service, or distributed operational burden. Its component topology was introduced on 2026-07-21 and finalized in the implemented form recorded here on 2026-07-22.

The system still needs clear ownership boundaries around browser interaction, workflow and persistence, caption execution, object storage, and local orchestration.

## Decision

CANVAS remains a monorepo with these runtime components:

- a React and TypeScript frontend;
- a Spring Boot modular-monolith backend;
- PostgreSQL for application state;
- two private S3-compatible object-storage buckets, separated into artwork originals and generated assets; and
- a separate FastAPI caption worker.

Docker Compose orchestrates these components for local use. Published host ports bind to loopback so the local services are not exposed on all host interfaces.

Java 25, Node.js 24, and Python 3.13 are the project runtime baselines. Flyway owns forward schema migrations beginning with the first persisted schema. No additional deployable service is added without demonstrated operational need; business modules remain within the backend unless extraction becomes justified.

## Alternatives considered

- A single-language application. This would reduce toolchains but either weaken the browser ecosystem fit or couple machine-learning integration to the Java backend.
- Microservices or an event-driven decomposition. This would enable independent deployment at the cost of unnecessary local and operational complexity.
- Local filesystem storage. This is simple for one process but weakens portability, separation, and the path to hosted object storage.
- Managed-cloud-only development. This could resemble a production deployment but would add cost, credentials, network dependence, and a harder contributor setup.

## Consequences

The complete MVP can run through one local orchestration layer, while the backend keeps workflow ownership in a single transactional application. Caption and storage boundaries remain replaceable.

Contributors must maintain Java, JavaScript, and Python toolchains and coordinate typed contracts between the frontend, backend, and caption worker. Compose, migrations, storage bootstrap, and health checks also require coordinated changes when a boundary evolves.

## Reversal or migration path

Backend modules may be extracted into separate deployments when operational evidence justifies the change. PostgreSQL, S3-compatible storage, the caption worker, or local orchestration may be replaced behind their existing contracts. Schema changes continue through forward Flyway migrations so existing data can be carried into a new topology.

## References

- [ADR 0001: Project Foundation](0001-project-foundation.md)
- [Architecture](../architecture.md)
- [CANVAS local MVP design](../superpowers/specs/2026-07-21-canvas-local-mvp-design.md)
- [CANVAS local MVP implementation plan](../superpowers/plans/2026-07-21-canvas-local-mvp.md)
