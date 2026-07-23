# Presentable Project Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the repository's project-facing documentation with accurate, concise vision, cost, roadmap, overview, and architecture guidance grounded in the implemented local MVP and accepted ADRs.

**Architecture:** Rewrite the incomplete vision, cost, and roadmap documents in place, then correct the project overview and architecture where final review finds stale planned-model or narration claims. Use links to authoritative product, decision, and acceptance records rather than duplicating them. Separate product narrative, economic constraints, milestones, local operation, and component boundaries so each file has one clear responsibility.

**Tech Stack:** Markdown, Git, repository-local shell verification.

## Global Constraints

- Project-facing documentation scope is `README.md`, `VISION.md`, `docs/architecture.md`, `docs/cost-principles.md`, and `docs/roadmap.md`. Keep this approved design and plan aligned when final review expands or clarifies the documentation-only correction.
- Describe the implemented local MVP accurately and distinguish it from production readiness.
- Distinguish implemented behavior, accepted direction, and future hypotheses.
- Do not promise delivery dates, production costs, accessibility conformance, model quality, or unvalidated vendors.
- Preserve explicit human approval, accessibility, nonprofit affordability, privacy/consent, auditable publication, and component replaceability as durable principles.
- Do not change product scope, accepted ADR content or status, implementation, dependencies, configuration, APIs, or deployment. Correcting the architecture document records existing behavior; it does not authorize an architecture change.
- Use relative Markdown links to authoritative repository documents rather than duplicating them.
- Do not use `_work in progress_`, placeholder headings, empty sections, `TBD`, or unresolved `TODO` text.

---

### Task 1: Complete the product vision and cost principles

**Files:**
- Modify: `VISION.md`
- Modify: `docs/cost-principles.md`

**Interfaces:**
- Consumes: current project status from `README.md`, boundaries from `docs/product-scope.md`, component direction from `docs/architecture.md`, and accepted constraints from `docs/decisions/README.md`.
- Produces: the durable product narrative and economic decision framework used by the roadmap in Task 2.

- [ ] **Step 1: Rewrite `VISION.md` as the product-level narrative**

Use this exact top-level structure:

```markdown
# CANVAS vision

## Why CANVAS exists
## Who CANVAS serves
## What makes CANVAS different
## The editorial workflow
## Non-negotiable principles
## Product boundaries
## What success looks like
## Current status
## Related documents
```

The content must state:

- visual artwork is often exhibited or published without useful descriptions for blind and low-vision audiences;
- CANVAS serves those audiences plus artists, museums, galleries, community organizations, and people preparing accessible content;
- CANVAS is an editorial publication workflow, not a generic image-captioning tool;
- machine-generated content is optional draft material and never publication authority;
- an artwork can have zero or more ordered descriptions with free-form labels, entered manually, imported in a future workflow, or generated as drafts;
- humans review and explicitly approve exact revisions before publication;
- approved public content remains available as text even when audio exists, and QR codes provide a physical-to-digital path;
- non-negotiable principles include human approval, factual and respectful descriptions, accessible operation, privacy/consent, auditable publication, affordable operation, and replaceable model/storage boundaries;
- CANVAS is not a general CMS, digital asset manager, image editor, marketplace, social network, or automatic publishing engine;
- success means organizations can publish trustworthy descriptions without an ML team or cloud engineers while blind and low-vision people participate in evaluation;
- the repository currently contains an admin-only local demo with placeholder caption/audio behavior, not a production-ready or fully human-accessibility-validated service.

Link the Related documents section to `README.md`, `docs/product-scope.md`, `docs/architecture.md`, `docs/cost-principles.md`, `docs/roadmap.md`, `docs/decisions/README.md`, and `docs/manual-testing.md` using paths relative to `VISION.md`.

- [ ] **Step 2: Rewrite `docs/cost-principles.md` as operating guidance**

Use this exact top-level structure:

```markdown
# Cost principles

## Purpose
## Operating assumptions
## Cost model
### Fixed or baseline costs
### Variable costs
## Architectural implications
## Requirements for paid managed services
## Cost review triggers
## Definition of success
## Related documents
```

The content must state:

- small nonprofits and predictable operations are the priority;
- the aspirational steady-state idle target is below USD 50 per month, but it is not a quote, budget commitment, or permanent guarantee;
- likely baseline categories are application hosting, managed or self-operated PostgreSQL, and object storage;
- variable categories are caption inference, future audio generation, bandwidth/egress, storage growth, and batch workload growth;
- GPU inference should scale to zero; generated audio, captions, thumbnails, and QR assets should be cached and reused; idle infrastructure must not dominate expected usage costs;
- object storage remains behind a private S3-compatible abstraction;
- variable cost per job/artwork should be observable where practical;
- a proposed paid managed service must document expected cost, pricing assumptions, free-tier limitations, simpler alternatives, portability/exit path, and review conditions;
- review triggers include a new paid vendor, permanent worker, material storage/egress growth, workload-pattern changes, and movement from demo to production;
- success means a small organization can operate CANVAS without cloud engineering staff and can understand what drives its bill.

Do not select a provider. State explicitly that free tiers may change or disappear and cannot define the production cost model.

Link Related documents to `../README.md`, `product-scope.md`, `architecture.md`, `roadmap.md`, and `decisions/README.md` using paths relative to `docs/cost-principles.md`.

- [ ] **Step 3: Verify vision and cost documentation**

Run:

```bash
! head -n 1 VISION.md | rg -q '^_work in progress_$'
! head -n 1 docs/cost-principles.md | rg -q '^_work in progress_$'
rg -q '^## Current status$' VISION.md
rg -q 'not a quote, budget commitment, or permanent guarantee' docs/cost-principles.md
rg -q 'free tiers.*change or disappear' docs/cost-principles.md
! rg -n 'TBD|TODO|FIXME|XXX|^## [^[:alnum:]]*$' VISION.md docs/cost-principles.md
git diff --check
```

Expected: both WIP markers are absent; status, cost disclaimer, and free-tier warning exist; no placeholders are found; `git diff --check` exits zero.

Manually confirm that the vision distinguishes the local demo from production and that neither file claims a vendor, accessibility conformance, or guaranteed cost.

- [ ] **Step 4: Commit**

```bash
git add VISION.md docs/cost-principles.md
git commit -m "docs: complete vision and cost principles"
```

---

### Task 2: Replace the obsolete phase outline with a current-state roadmap

**Files:**
- Modify: `docs/roadmap.md`

**Interfaces:**
- Consumes: current status from `README.md`, future boundaries from `docs/product-scope.md`, accessibility work from `docs/manual-testing.md`, accepted decisions from `docs/decisions/README.md`, and vision/cost principles from Task 1.
- Produces: a milestone-oriented roadmap that separates completed baseline, committed next validation work, gated future capabilities, and non-committed opportunities.

- [ ] **Step 1: Rewrite `docs/roadmap.md` around current state and next milestones**

Use this exact top-level structure:

```markdown
# CANVAS roadmap

## How to read this roadmap
## Current baseline: Local MVP
## Milestone 1: Accessibility validation and demo hardening
### Goal
### Deliverables
### Success criteria
### Exclusions and decision gates
## Milestone 2: Real caption-model integration
### Goal
### Deliverables
### Success criteria
### Exclusions and decision gates
## Milestone 3: Organizational content workflows
### Goal
### Candidate deliverables
### Success criteria
### Exclusions and decision gates
## Milestone 4: Production readiness
### Goal
### Deliverables
### Success criteria
### Exclusions and decision gates
## Later opportunities
## Explicitly out of scope
## Related documents
```

`How to read this roadmap` must state that milestones are ordered by dependency, not promised dates; later scope is subject to user research, cost review, accessibility validation, and ADRs.

The current baseline must accurately list the admin-only local workflow: authenticated image upload; zero or more ordered free-form descriptions; manual and deterministic placeholder-generated drafts; revision history; explicit approval; immutable publication snapshots; public text; cached placeholder WAV audio and QR assets; PostgreSQL/MinIO persistence; and automated unit/integration/component/E2E evidence. It must state that real model integration and the full human accessibility checklist remain incomplete.

Milestone 1 must cover full keyboard/visible-focus, audible screen-reader, 200 percent zoom/320 CSS-pixel, contrast/non-color validation, remediation, Node 24-supported verification, and repeatable demo setup/reset. Success requires recorded evidence and no unresolved release-blocking accessibility issue, without claiming formal certification.

Milestone 2 must preserve the existing caption contract and editorial workflow while evaluating a real model/provider. Cover description-quality evaluation with blind/low-vision and editorial input, bounded async inference, cost/latency observation, privacy/content-safety review, optional model-heavy tests, and fallback behavior. Generated content remains draft-only. Do not preselect JoyCaption, RunPod, or any other vendor.

Milestone 3 must describe organizational work as candidate scope requiring research and new ADRs: persistent users/organizations/roles, multiple audit identities, optional import of zero or more existing descriptions, artwork grouping, batch aids, and access controls. Success is a representative pilot collection with ownership and auditability. Do not promise a public artist portal or exact role model.

Milestone 4 must cover deployment evaluation, backup/restore, monitoring, secrets, retention/privacy, abuse/rate controls, security review, recovery, cost reporting, provider exit path, and accessibility release gates. Success requires tested deployment/recovery, documented expected costs, and clear operational ownership; it does not promise a specific host or SLA.

Later opportunities may include CMS/museum integrations, analytics with privacy review, localization, exhibitions/collections, batch imports, and model research. Explicitly out of scope must preserve NFT support, marketplace, social network, image editing, general-purpose digital asset management, and general CMS replacement.

Link Related documents to `../README.md`, `../VISION.md`, `product-scope.md`, `architecture.md`, `cost-principles.md`, `manual-testing.md`, and `decisions/README.md`.

- [ ] **Step 2: Verify roadmap content and repository presentation**

Run:

```bash
! rg -l -i '^_work in progress_$' --glob '*.md' .
rg -q '^## Current baseline: Local MVP$' docs/roadmap.md
rg -q '^## Milestone 1: Accessibility validation and demo hardening$' docs/roadmap.md
rg -q '^## Milestone 2: Real caption-model integration$' docs/roadmap.md
rg -q '^## Milestone 3: Organizational content workflows$' docs/roadmap.md
rg -q '^## Milestone 4: Production readiness$' docs/roadmap.md
rg -q 'ordered by dependency, not promised dates' docs/roadmap.md
! rg -n 'TBD|TODO|FIXME|XXX' docs/roadmap.md
git diff --check
```

Expected: no Markdown WIP marker remains, every milestone exists, the no-date disclaimer exists, no placeholders/empty milestone headings remain, and `git diff --check` exits zero.

Manually confirm that every milestone subsection contains prose or a non-empty list. Compare the current baseline to `README.md` and ADRs 0003–0007; confirm the roadmap preserves product-scope exclusions and does not select a vendor or claim completed human accessibility validation.

- [ ] **Step 3: Commit**

```bash
git add docs/roadmap.md
git commit -m "docs: replace obsolete roadmap with current milestones"
```

---

### Task 3: Correct current-workflow and architecture presentation

**Files:**
- Modify: `README.md`
- Modify: `docs/architecture.md`
- Modify: `docs/superpowers/specs/2026-07-22-presentable-project-docs-design.md`
- Modify: `docs/superpowers/plans/2026-07-22-presentable-project-docs.md`

**Interfaces:**
- Consumes: implemented behavior from `compose.yaml`, backend and worker code/configuration, and accepted boundaries from ADRs 0003, 0006, and 0007.
- Produces: a truthful entry-point overview, a complete implemented architecture reference, and design/plan records that describe the full presentability scope.

- [ ] **Step 1: Correct the README's current MVP claims**

State that the current generated draft is deterministic placeholder text derived only from submitted metadata and does not analyze the image. State that the current generic placeholder WAV does not narrate approved text. Present the modular-monorepo topology as implemented, not planned, and describe the worker as the current model-free FastAPI contract.

Remove any Technology direction preselection of JoyCaption, RunPod, or another production model, GPU provider, hosting vendor, or text-to-speech service. State that real caption-model, GPU/execution-provider, and text-specific audio integrations remain future evaluations behind the existing contracts and human-approval rules.

- [ ] **Step 2: Replace the incomplete architecture page**

Document:

- the implemented React, Spring Boot modular-monolith, PostgreSQL, private S3-compatible storage, FastAPI worker, and Compose topology;
- complete responsibilities for frontend, backend modules, worker, persistence, storage, and local infrastructure;
- the current worker's metadata-only deterministic response and absence of model or GPU inference;
- backend authority for authentication, revisions, approval, job orchestration, asset association, and publication;
- upload, draft, approval, publication, and public-delivery data flow;
- PostgreSQL/object-storage separation, private original/generated buckets, cached content identities, immutable publication snapshots, and current-snapshot public authorization; and
- evidence and ADR gates for future models, providers, audio generation, or additional services.

Do not invent production behavior or select a vendor.

- [ ] **Step 3: Align the approved design and plan**

Expand the design and plan scope from only vision/cost/roadmap completion to include the necessary README and architecture corrections. Preserve the documentation-only boundary, state that product scope and accepted ADR content remain unchanged, and add verification for implemented-claim accuracy, placeholder-versus-real capability wording, vendor neutrality, relative links, incomplete content, and diff cleanliness.

- [ ] **Step 4: Verify the final documentation set**

Validate README and architecture claims against the code/configuration and ADRs 0003, 0006, and 0007, then run focused checks:

```bash
! rg -l -i '^_work in progress_$' --glob '*.md' .
! rg -n 'TBD|TODO|FIXME|XXX|^## [^[:alnum:]]*$' \
    README.md VISION.md docs/architecture.md docs/cost-principles.md docs/roadmap.md
rg -q 'deterministic, model-free placeholder caption worker' README.md
rg -q 'does not narrate the approved text' README.md
rg -q '^## Current architecture$' README.md
rg -q '^## Implemented topology$' docs/architecture.md
rg -q '^## Component responsibilities$' docs/architecture.md
rg -q '^## Workflow and data flow$' docs/architecture.md
! rg -n 'JoyCaption|RunPod' README.md docs/architecture.md VISION.md docs/roadmap.md
git diff --check
```

Check every relative Markdown link in the repository and manually confirm that project-facing documents do not present a real model, GPU provider, production host, or text-to-speech service as selected. Historical references in accepted ADRs and implementation records are not current vendor selections and remain unchanged.

- [ ] **Step 5: Commit the final correction as one focused change**

```bash
git add README.md docs/architecture.md \
  docs/superpowers/specs/2026-07-22-presentable-project-docs-design.md \
  docs/superpowers/plans/2026-07-22-presentable-project-docs.md
git commit -m "docs: correct current MVP architecture presentation"
```
