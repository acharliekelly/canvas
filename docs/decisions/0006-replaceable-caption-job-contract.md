# ADR 0006: Replaceable Caption Job Contract

**Status:** Accepted

**Decision date:** 2026-07-21

**Recorded date:** 2026-07-22

## Context

Caption generation must not couple the Spring Boot application to Python machine-learning libraries or make GPU access part of routine local development and testing. Caption work may fail independently and must not damage artwork metadata or manual descriptions. This retrospective record documents the caption boundary implemented on 2026-07-21.

The MVP needs to prove the workflow and failure handling before selecting a production model, GPU host, or caption provider.

## Decision

The backend owns persistent caption-job orchestration and calls a separate Python worker through a typed HTTP request and response contract. Job lifecycle state is explicit as `PENDING`, `RUNNING`, `SUCCEEDED`, or `FAILED`; incomplete work can be recovered, and retries safely reuse an active or successful job or create a new attempt after failure.

The MVP FastAPI worker is deterministic and model-free. It identifies its placeholder engine and produces text from submitted metadata without claiming image analysis. A successful result enters the shared description workflow as a `GENERATED` draft and still requires human review and approval.

A real model, GPU runtime, and hosted or managed provider integration are deferred. The contract does not select JoyCaption or any provider as the current implementation.

## Alternatives considered

- Embed Python or machine-learning dependencies in Spring Boot. This would blur runtime ownership and place model-specific tooling inside the workflow application.
- Perform synchronous inference in the upload request. This would couple upload latency and reliability to potentially long-running inference and make safe recovery harder.
- Couple the backend directly to JoyCaption or a hosted caption API. This could accelerate one integration but would make an undecided model or provider part of the application contract.

## Consequences

Local operation includes one additional process and an HTTP boundary that must be monitored and evolved compatibly. Persistent job state, bounded execution, explicit failures, and retry behavior add orchestration code.

In return, the worker implementation is replaceable, the backend retains editorial authority, and default tests need no GPU or external model. Caption failures remain separate from artwork and manual-description data.

## Reversal or migration path

The deterministic worker may be replaced with another implementation of the same contract. If new capabilities require incompatible fields or semantics, an adapter or versioned contract can be introduced while preserving persisted job history and the rule that generated output enters as an unapproved draft.

## References

- [ADR 0001: Project Foundation](0001-project-foundation.md)
- [Architecture](../architecture.md)
- [Cost principles](../cost-principles.md)
- [CANVAS local MVP design](../superpowers/specs/2026-07-21-canvas-local-mvp-design.md)
- [CANVAS local MVP implementation plan](../superpowers/plans/2026-07-21-canvas-local-mvp.md)
