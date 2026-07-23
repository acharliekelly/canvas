# ADR Governance and Retrospective Records Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make ADRs a required CANVAS project workflow and add focused records for the consequential technical decisions implemented by the local MVP.

**Architecture:** Keep ADR governance in `AGENTS.md` and `docs/decisions/README.md`, preserve ADR 0001 as historical context, and add accepted ADRs 0002 through 0007. The work is documentation-only and must describe current behavior without converting routine implementation details or deferred vendors into permanent architecture policy.

**Tech Stack:** Markdown, Git, repository-local shell verification.

## Global Constraints

- Store all architecture decision records in `docs/decisions/`.
- Use sequential four-digit identifiers and kebab-case filenames: `NNNN-short-title.md`.
- Every ADR includes status and dates, context, decision, alternatives considered, consequences, reversal or migration path, and references.
- Accepted ADRs are historical records; supersede them with a new ADR instead of silently rewriting the accepted decision.
- Update the ADR index whenever a record is added or its status changes.
- Preserve `docs/decisions/0001-project-foundation.md` unchanged in substance.
- Retroactive ADRs state both the original implementation date and documentation date when those differ.
- Do not present undecided model, GPU, cloud, or managed-service vendors as settled.
- Do not modify production code, tests, schemas, dependencies, APIs, or deployment configuration.

---

### Task 1: Establish ADR governance and discoverability

**Files:**
- Modify: `AGENTS.md`
- Create: `docs/decisions/README.md`
- Modify: `README.md`

**Interfaces:**
- Produces: mandatory contributor rules for ADR creation, lifecycle, and indexing.
- Produces: the canonical ADR index and template consumed by Task 2 and future contributors.

- [ ] **Step 1: Add the project-level directive to `AGENTS.md`**

Replace the general decision-record guidance under `## Documentation and decisions` with explicit requirements:

```markdown
Store architecture decision records in `docs/decisions/`. Use the next sequential four-digit identifier and a kebab-case filename: `NNNN-short-title.md`.

Create or supersede an ADR in the same change whenever a consequential choice changes system boundaries, persistence or publication semantics, security, an external contract, deployment topology, operating-cost posture, or an accepted decision's constraints. Routine implementation details do not require an ADR.

Every ADR must include context, decision, alternatives considered, consequences, a reversal or migration path, and references. Update `docs/decisions/README.md` whenever an ADR is added or its status changes. Accepted ADRs are historical records: do not silently rewrite them; create a new ADR that supersedes the earlier record.
```

Retain the existing instruction to update documentation when behavior, architecture, operating cost, or scope changes and the warning against presenting undecided vendors as settled.

- [ ] **Step 2: Create the ADR index and lifecycle guide**

Create `docs/decisions/README.md` with:

```markdown
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
```

Add an ordered table for ADRs 0001 through 0007 with columns `ADR`, `Title`, `Status`, `Decision date`, and `Recorded date`. Link every ADR filename. Use decision date `2026-07-21` for ADRs 0001, 0004, and 0006; use decision date `2026-07-22` for ADRs 0002, 0003, 0005, and 0007; and use recorded date `2026-07-22` for ADRs 0002 through 0007. For ADRs 0003, 0005, and 0007, note where implementation began on 2026-07-21 but the recorded decision reflects the finalized implementation on 2026-07-22.

Add a copyable template containing these exact headings:

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

- [ ] **Step 3: Link the canonical index from `README.md`**

Add `- [Architecture Decision Records](docs/decisions/README.md)` to the README's Documentation list. Keep the existing architecture, scope, roadmap, cost, design, and contributor links.

- [ ] **Step 4: Verify governance documentation**

Run:

```bash
rg -n "docs/decisions|NNNN-short-title|supersed|Reversal or migration path" AGENTS.md docs/decisions/README.md README.md
git diff --check
```

Expected: the directive, lifecycle, template, and README link are present; `git diff --check` exits zero.

- [ ] **Step 5: Commit**

```bash
git add AGENTS.md README.md docs/decisions/README.md
git commit -m "docs: require architecture decision records"
```

---

### Task 2: Record the local MVP technical decisions

**Files:**
- Create: `docs/decisions/0002-architecture-decision-record-governance.md`
- Create: `docs/decisions/0003-local-mvp-deployment-topology.md`
- Create: `docs/decisions/0004-admin-only-session-authentication.md`
- Create: `docs/decisions/0005-revisioned-description-and-publication-model.md`
- Create: `docs/decisions/0006-replaceable-caption-job-contract.md`
- Create: `docs/decisions/0007-immutable-generated-assets-and-storage.md`
- Modify: `docs/decisions/README.md`

**Interfaces:**
- Consumes: the ADR format, lifecycle, numbering, and index rules from Task 1.
- Produces: accepted records describing the durable decisions implemented by the local MVP.

- [ ] **Step 1: Write ADR 0002 for ADR governance**

Record `Accepted`, decision date `2026-07-22`, and recorded date `2026-07-22`. State that consequential decisions live in sequential records under `docs/decisions/`, the index is updated with status changes, accepted ADRs are superseded rather than rewritten, and the required sections make trade-offs and reversibility explicit.

Alternatives must include decisions only in general documentation, a single continually rewritten architecture document, and ADRs for every implementation detail. Consequences must acknowledge the maintenance cost and possible need for judgment about what is consequential. The reversal path may replace the workflow with a new governance ADR while preserving existing files as history.

- [ ] **Step 2: Write ADR 0003 for the local MVP deployment topology**

Record `Accepted`, decision date `2026-07-22`, and recorded date `2026-07-22`, noting that implementation began on 2026-07-21 before the topology was finalized. State that the monorepo contains a React frontend, Spring Boot modular-monolith backend, PostgreSQL, two private S3-compatible storage buckets, and a separate FastAPI caption worker, locally orchestrated by Docker Compose. Record Java 21, Node 24, and Python 3.13 as project baselines; Flyway owns schema migrations from the first persisted schema; published host ports bind to loopback; and no additional deployable service is added without demonstrated operational need.

Alternatives must include a single-language application, microservices/event-driven decomposition, local filesystem storage, and managed-cloud-only development. Consequences must cover simple local operation, multiple runtime toolchains, replaceable ML/storage boundaries, and the need to coordinate contracts. The reversal path must allow extracting modules or changing infrastructure behind existing contracts and migrations.

- [ ] **Step 3: Write ADR 0004 for admin-only session authentication**

Record `Accepted`, decision date `2026-07-21`, and recorded date `2026-07-22`. State that the MVP has one environment-configured administrator, that the documented local/default configuration uses a BCrypt-encoded password hash while the runtime accepts Spring Security `{id}` formats supported by its delegating password encoder, and that the application uses a server-side HTTP-only session cookie, CSRF protection for mutations, and JSON 401/403 API responses. Explicitly defer registration, organizations, roles, password recovery, and external identity providers.

Alternatives must include no authentication, HTTP Basic authentication, token/JWT authentication, and an external identity provider. Consequences must cover low demo complexity, server-side session state, environment-secret handling, and lack of multi-user audit identity. The reversal path must preserve domain ownership/audit fields while introducing persistent users and organizations through migrations.

- [ ] **Step 4: Write ADR 0005 for revisioned descriptions and publication snapshots**

Record `Accepted`, decision date `2026-07-22`, and recorded date `2026-07-22`, noting that the core implementation began on 2026-07-21 before its revision-safety behavior was finalized. State that artwork owns zero or more ordered descriptions with free-form labels; manual and generated descriptions share revisioned `DRAFT` and `APPROVED` states; publication requires at least one approved revision; public payloads expose only the exact approved revisions captured by an immutable publication snapshot; and only an exact match to the current snapshot is idempotent. Returning from A to B to A creates a new audit event with a monotonically increasing publication version.

Alternatives must include one description per artwork, fixed Objective/Subjective columns, mutable in-place text, and publishing current description rows directly. Consequences must cover optional description generation, historical auditability, additional schema complexity, and immutable public provenance. The reversal path must use forward migrations and derived read models without deleting historical revisions or snapshots.

- [ ] **Step 5: Write ADR 0006 for the caption job contract**

Record `Accepted`, decision date `2026-07-21`, and recorded date `2026-07-22`. State that the backend owns persistent job orchestration and invokes a separate Python worker through a typed HTTP contract; the MVP worker is deterministic and model-free; generated text enters as a draft; lifecycle state is explicit and retry-safe; and real model, GPU, and provider integration are deferred.

Alternatives must include embedding Python/ML in Spring Boot, synchronous inference in the upload request, and coupling directly to JoyCaption or a hosted API. Consequences must cover an extra local process, stable replaceability, no-GPU default tests, and the need to evolve the contract compatibly. The reversal path must permit swapping worker implementations or adding an adapter/versioned contract without changing editorial semantics.

- [ ] **Step 6: Write ADR 0007 for immutable generated assets and storage**

Record `Accepted`, decision date `2026-07-22`, and recorded date `2026-07-22`, noting that the asset pipeline began on 2026-07-21 before its association, repair, and credential boundaries were finalized. State that audio identity and association derive from exact approved published text even though the MVP generator returns generic placeholder WAV bytes, and that QR codes derive from exact publication URLs; metadata and objects are cached by deterministic content identity; publication snapshots persist exact audio/QR asset associations; public routes contain generated-asset UUIDs and use immutable caching; missing objects are repaired under the same-key lock without duplicate metadata; originals and generated assets use separate private buckets; and MinIO root credentials are bootstrap-only while the backend uses a scoped application identity.

Alternatives must include regenerating on every request, stable mutable asset aliases, database blobs/local filesystem storage, one shared bucket, and backend use of root credentials. Consequences must cover input-derived provenance, the generic MVP placeholder bytes, lower repeated cost, stronger isolation, repair complexity, and storage metadata lifecycle. The reversal path must preserve asset URLs belonging to the currently published snapshot while introducing new generators, storage providers, or key namespaces through versioned metadata and forward migrations; superseded snapshot URLs may return 404 under the current authorization model.

- [ ] **Step 7: Add references and verify the entire ADR set**

Each ADR must reference the relevant subset of:

- `0001-project-foundation.md`;
- `../architecture.md`;
- `../product-scope.md`;
- `../cost-principles.md`;
- `../superpowers/specs/2026-07-21-canvas-local-mvp-design.md`;
- `../superpowers/plans/2026-07-21-canvas-local-mvp.md`.

Run:

```bash
test "$(find docs/decisions -maxdepth 1 -name '[0-9][0-9][0-9][0-9]-*.md' | wc -l)" -eq 7
for file in docs/decisions/000{2,3,4,5,6,7}-*.md; do rg -q '^## Context$' "$file"; rg -q '^## Decision$' "$file"; rg -q '^## Alternatives considered$' "$file"; rg -q '^## Consequences$' "$file"; rg -q '^## Reversal or migration path$' "$file"; rg -q '^## References$' "$file"; done
rg -n "TBD|TODO|implement later|fill in" docs/decisions && exit 1 || true
git diff --check
```

Expected: seven numbered ADR files exist, all six new ADRs contain every required heading, the placeholder scan finds nothing, and `git diff --check` exits zero.

Manually verify that the index contains every ADR exactly once, links resolve, dates/statuses match, ADR 0001 is unchanged in substance, and no record claims a deferred model or provider is selected.

- [ ] **Step 8: Commit**

```bash
git add docs/decisions
git commit -m "docs: record local MVP architecture decisions"
```
