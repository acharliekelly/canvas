# ADR Governance and Retrospective Decision Records Design

**Status:** Approved

**Date:** 2026-07-22

## Goal

Make architecture decision records a required part of the CANVAS project workflow and document the consequential technical decisions established by the local MVP.

## Scope

This change is documentation-only. It does not alter application behavior, dependencies, schemas, APIs, or deployment configuration.

The work will preserve `docs/decisions/0001-project-foundation.md` as the foundational historical record. New focused ADRs will document durable MVP decisions without attempting to turn every library or implementation detail into architecture policy.

## Project workflow directive

`AGENTS.md` will require contributors to:

- store decision records in `docs/decisions/`;
- use sequential four-digit identifiers and kebab-case filenames in the form `NNNN-short-title.md`;
- record consequential choices in the same change that introduces or materially changes them;
- include context, decision, alternatives considered, consequences, and reversal or migration path;
- treat accepted ADRs as historical records and supersede them with a new ADR rather than silently rewriting the original decision;
- update the ADR index whenever a record is added or its status changes.

Consequential choices include changes to system boundaries, persistence or publication semantics, security models, external contracts, operating-cost posture, deployment topology, or an accepted decision's constraints. Routine implementation details do not require an ADR.

## ADR index and template

`docs/decisions/README.md` will provide:

- an ordered index containing ADR number, title, status, and date;
- status definitions for `Proposed`, `Accepted`, `Superseded`, and `Rejected`;
- a copyable ADR template;
- guidance distinguishing a consequential decision from routine implementation work;
- the rule that retroactive ADRs identify both the original implementation date and the documentation date when those differ.

The template will use these sections:

1. Status and dates
2. Context
3. Decision
4. Alternatives considered
5. Consequences
6. Reversal or migration path
7. References

## Retrospective ADR set

The following records will be added with `Accepted` status:

### ADR 0002: Architecture decision record governance

Records the ADR location, numbering, lifecycle, required content, index maintenance, and supersession policy. This makes the documentation workflow itself an explicit project decision.

### ADR 0003: Local MVP deployment topology

Records the modular monorepo topology: React frontend, Spring Boot modular-monolith backend, PostgreSQL, private S3-compatible storage, and a separate FastAPI caption worker orchestrated locally with Docker Compose. It also records supported runtime baselines, loopback-only published ports, database migrations from the first schema, and the decision not to add more deployable services without operational need.

### ADR 0004: Admin-only session authentication

Records the single environment-configured administrator, server-side session cookies, CSRF protection, JSON API failure behavior, and the deliberate deferral of user registration, organizations, roles, password recovery, and external identity providers.

### ADR 0005: Revisioned description and publication model

Records that an artwork owns zero or more ordered, free-form-labeled descriptions; manual and generated content follow revisioned draft and approved states; publication requires at least one explicitly approved revision; and each publication is an immutable, auditable snapshot. Only a request identical to the current publication is idempotent, so returning to older content creates a new publication event.

### ADR 0006: Replaceable caption job contract

Records that the backend owns job orchestration and persistence while caption inference remains behind an HTTP contract in a separate Python worker. The local MVP uses a deterministic placeholder, requires no GPU, and treats generated content as a draft. Jobs expose explicit lifecycle state and are designed for safe retries; real model and provider integration remain deferred.

### ADR 0007: Immutable generated assets and storage

Records that audio identity and publication association derive from exact approved published revisions even though the MVP generator returns generic placeholder WAV bytes, and that QR codes derive from exact publication URLs. Generated assets are cached by deterministic content identity; public assets use versioned identifiers authorized through the current publication snapshot, so superseded snapshot URLs may return 404; missing objects may be repaired without duplicating metadata; and original and generated objects use separate private buckets. MinIO root credentials are bootstrap-only while the backend uses a bucket-scoped application identity.

## Existing ADR treatment

ADR 0001 remains accepted and unchanged in substance. Its broad foundational decisions provide historical context; the focused ADRs refine implemented MVP choices and link back to ADR 0001 where applicable. New records must not claim that deferred vendors or model integrations are settled.

## Documentation integration

The README documentation section will link to the ADR index rather than only relying on contributors to discover the directory. Cross-references from the new ADRs will use relative Markdown links and point to relevant product, architecture, cost, design, or implementation-plan documents.

## Verification

Documentation verification will confirm:

- ADR identifiers are unique and sequential from 0001 through 0007;
- every indexed ADR exists and every ADR appears in the index;
- each new ADR contains all required sections and no placeholder text;
- statuses and dates agree between the records and index;
- relative Markdown links resolve to repository files;
- `git diff --check` passes;
- the new records describe current implemented behavior without contradicting product scope, architecture boundaries, cost principles, or the local MVP design.

## Deferred work

This change will not create ADRs for ordinary dependency versions, individual test tools, UI component choices, or model/provider selection that remains undecided. Future milestones should add or supersede ADRs only when they make consequential decisions.
