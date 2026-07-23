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

## Dates and historical records

Use **Decision date** for the date the decision was made or the implementation was introduced, and **Recorded date** for the date the ADR was documented. When a retrospective ADR records an already-implemented decision, identify the original implementation or introduction date and the recorded date when they differ. If later work finalizes or materially refines the decision, state both the original introduction date and the later finalization or refinement date in the record's context; use the later date as the decision date only when that later work is the decision being recorded.

ADR 0001 predates this governance and uses a legacy multi-decision format. Preserve it as historical evidence; it is not a template for new records. New ADRs and superseding ADRs must use the standard sections in the template below.

## Index

| ADR | Title | Status | Decision date | Recorded date |
| --- | --- | --- | --- | --- |
| [0001-project-foundation.md](0001-project-foundation.md) | Project Foundation (legacy format) | Accepted | 2026-07-21 | — |
| [0002-architecture-decision-record-governance.md](0002-architecture-decision-record-governance.md) | Architecture Decision Record Governance | Accepted | 2026-07-22 | 2026-07-22 |
| [0003-local-mvp-deployment-topology.md](0003-local-mvp-deployment-topology.md) | Local MVP Deployment Topology | Accepted | 2026-07-22 | 2026-07-22 |
| [0004-admin-only-session-authentication.md](0004-admin-only-session-authentication.md) | Admin-Only Session Authentication | Accepted | 2026-07-21 | 2026-07-22 |
| [0005-revisioned-description-and-publication-model.md](0005-revisioned-description-and-publication-model.md) | Revisioned Description and Publication Model | Accepted | 2026-07-22 | 2026-07-22 |
| [0006-replaceable-caption-job-contract.md](0006-replaceable-caption-job-contract.md) | Replaceable Caption Job Contract | Accepted | 2026-07-21 | 2026-07-22 |
| [0007-immutable-generated-assets-and-storage.md](0007-immutable-generated-assets-and-storage.md) | Immutable Generated Assets and Storage | Accepted | 2026-07-22 | 2026-07-22 |

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
