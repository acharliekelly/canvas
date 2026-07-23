# ADR 0002: Architecture Decision Record Governance

**Status:** Accepted

**Decision date:** 2026-07-22

**Recorded date:** 2026-07-22

## Context

CANVAS has durable architectural constraints spanning editorial workflow, security, deployment, external contracts, persistence, and operating cost. General project documentation describes the system, but it does not by itself provide a stable history of why each consequential choice was made or how that choice can be reversed.

The project needs enough decision discipline to preserve rationale without requiring a record for routine implementation work.

## Decision

Consequential architecture decisions are stored as sequential, four-digit records in `docs/decisions/`. Each record uses a kebab-case filename and includes status and dates, context, the decision, alternatives considered, consequences, a reversal or migration path, and references.

The decision index is updated whenever a record is added or its status changes. An accepted ADR is a historical record: a later material change supersedes it with a new linked ADR instead of silently rewriting the accepted decision. Routine implementation details and easily reversible local refactors do not require ADRs.

## Alternatives considered

- Record decisions only in general documentation. This keeps fewer files but loses a focused history of rationale, trade-offs, and status.
- Maintain one continually rewritten architecture document. This describes the current state well but erases the sequence and context of earlier accepted choices.
- Require an ADR for every implementation detail. This maximizes documentation but creates noise and maintenance cost disproportionate to reversible choices.

## Consequences

Contributors gain a discoverable, reviewable history of consequential decisions and their reversal paths. Changes to important constraints must account for the accepted record they replace.

The workflow adds maintenance cost. Contributors must also use judgment to decide whether a change is consequential enough to require an ADR, and that judgment may occasionally require review.

## Reversal or migration path

A later governance ADR may replace this workflow and supersede this record. Existing ADR files remain in the repository as historical evidence even if the indexing, status model, or required format changes.

## References

- [ADR 0001: Project Foundation](0001-project-foundation.md)
- [Architecture decision record index and lifecycle](README.md)
- [Agent and contributor guide](../../AGENTS.md)
