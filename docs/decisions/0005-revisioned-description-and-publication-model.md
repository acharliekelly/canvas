# ADR 0005: Revisioned Description and Publication Model

**Status:** Accepted

**Decision date:** 2026-07-22

**Recorded date:** 2026-07-22

## Context

CANVAS must support manual and generated editorial paths without treating machine output as final authority. Approved or published wording must remain auditable when an editor makes a later change. The core publication model was introduced on 2026-07-21 and finalized in the revision-safe implemented form recorded here on 2026-07-22.

Organizations may use different description categories, and an artwork may be uploaded before any description exists.

## Decision

An artwork owns zero or more descriptions with a stable order and free-form labels. Manual and generated descriptions use the same revision model. Revisions have explicit `DRAFT` and `APPROVED` states; editing an approved revision creates a new draft rather than mutating the approved text.

Publication requires at least one approved revision. A publication creates an immutable snapshot containing the exact approved revisions selected in their current order, including their labels and text. Public payloads expose only that current snapshot and never read draft text directly.

Only an exact match to the current publication snapshot is idempotent. Any changed approved revision or order creates a new snapshot with a monotonically increasing publication version. Consequently, publishing A, then B, then returning to A records a third publication event even though A existed historically.

## Alternatives considered

- One description per artwork. This cannot represent multiple complementary descriptions or organization-specific editorial practice.
- Fixed Objective and Subjective columns. This bakes one vocabulary into the schema and prevents free-form labels.
- Mutable in-place text. This is simpler to store but destroys approved and published provenance when edited.
- Publish current description rows directly. This avoids snapshot tables but allows later edits or ordering changes to alter public content without a publication event.

## Consequences

Caption generation remains optional, and manual and generated text receive the same human approval treatment. Historical revisions and publication events provide an auditable account of exactly what was public.

The model requires more tables, constraints, ordering rules, and transition logic than mutable description fields. Immutable snapshots and exact revision references provide strong public provenance, while repeated editorial cycles intentionally accumulate historical data.

## Reversal or migration path

Future presentation needs can be served through derived read models without changing the historical source of truth. Structural changes use forward migrations and must preserve existing description revisions, publication versions, and snapshot associations rather than deleting or rewriting them.

## References

- [ADR 0001: Project Foundation](0001-project-foundation.md)
- [Product scope](../product-scope.md)
- [CANVAS local MVP design](../superpowers/specs/2026-07-21-canvas-local-mvp-design.md)
- [CANVAS local MVP implementation plan](../superpowers/plans/2026-07-21-canvas-local-mvp.md)
