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
