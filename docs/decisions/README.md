# Architecture decision records

This directory contains consequential technical and product-architecture decisions for CANVAS. ADRs explain why a durable choice was made, its trade-offs, and how it can be reversed.

## When to write an ADR

Write or supersede an ADR when a change affects system boundaries, persistence or publication semantics, security, an external contract, deployment topology, operating-cost posture, or an accepted decision's constraints. Routine implementation details, dependency patch updates, and easily reversible local refactors do not require an ADR.

## Lifecycle

- `Proposed`: under discussion and not yet binding.
- `Accepted`: the current project decision.
- `Superseded`: replaced by a newer ADR, which must be linked.
- `Rejected`: considered but not adopted.

Accepted ADRs are historical records. Correct typographical errors without changing meaning; otherwise create a new ADR and mark the old record superseded.

## Index

| ADR | Title | Status | Decision date | Recorded date |
| --- | --- | --- | --- | --- |
| [0001-project-foundation.md](0001-project-foundation.md) | Project Foundation | Accepted | 2026-07-21 | — |
| [0002-architecture-decision-record-governance.md](0002-architecture-decision-record-governance.md) | Architecture Decision Record Governance | Accepted | 2026-07-22 | 2026-07-22 |
| [0003-local-mvp-deployment-topology.md](0003-local-mvp-deployment-topology.md) | Local MVP Deployment Topology | Accepted | 2026-07-21 | 2026-07-22 |
| [0004-admin-only-session-authentication.md](0004-admin-only-session-authentication.md) | Admin-Only Session Authentication | Accepted | 2026-07-21 | 2026-07-22 |
| [0005-revisioned-description-and-publication-model.md](0005-revisioned-description-and-publication-model.md) | Revisioned Description and Publication Model | Accepted | 2026-07-21 | 2026-07-22 |
| [0006-replaceable-caption-job-contract.md](0006-replaceable-caption-job-contract.md) | Replaceable Caption Job Contract | Accepted | 2026-07-21 | 2026-07-22 |
| [0007-immutable-generated-assets-and-storage.md](0007-immutable-generated-assets-and-storage.md) | Immutable Generated Assets and Storage | Accepted | 2026-07-21 | 2026-07-22 |

## Template

```markdown
# ADR NNNN: Title

**Status:** Proposed

**Decision date:** YYYY-MM-DD

**Recorded date:** YYYY-MM-DD

## Context

## Decision

## Alternatives considered

## Consequences

## Reversal or migration path

## References
```
