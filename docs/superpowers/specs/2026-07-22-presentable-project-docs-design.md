# Presentable Project Documentation Design

**Status:** Approved

**Date:** 2026-07-22

## Goal

Complete the repository's project-facing documentation with concise, authoritative descriptions grounded in the implemented local MVP, accepted ADRs, and current product direction. This includes replacing the remaining `_work in progress_` documents and correcting older overview and architecture text that still presents deferred model, GPU, and narration capabilities as current.

## Scope

Rewrite or correct:

- `VISION.md`;
- `docs/cost-principles.md`;
- `docs/roadmap.md`;
- `README.md`; and
- `docs/architecture.md`.

Keep this approved design and its implementation plan aligned with that documentation scope. The change is documentation-only. It records the implemented architecture accurately but does not alter product behavior, architecture, dependencies, configuration, APIs, deployment, product scope, or accepted ADRs.

## Documentation principles

- Describe the current repository accurately rather than presenting the original implementation sequence as future work.
- Distinguish implemented behavior, accepted direction, and future hypotheses.
- Link to product scope, architecture, cost principles, ADRs, and manual acceptance guidance instead of duplicating detailed records.
- Do not promise delivery dates, production costs, accessibility conformance, model quality, or unvalidated vendors.
- Treat accessibility, human editorial control, nonprofit affordability, and component replaceability as durable product principles.
- Keep the documents readable to potential contributors, nonprofit partners, accessibility reviewers, and technical evaluators.

## Vision document

`VISION.md` will explain:

- why CANVAS exists: artwork is frequently published or exhibited without useful visual descriptions;
- who it serves: blind and low-vision audiences, artists, museums, galleries, community organizations, and the people preparing accessible content;
- how it differs from generic image captioning: CANVAS is an editorial publication workflow in which machine output is optional draft material, not authority;
- the core value chain: artwork and context enter the system, one or more descriptions are created or imported, humans review and approve exact revisions, and approved content is published as text with associated audio and QR access;
- non-negotiable principles: explicit approval, factual/respectful description, text availability, accessible operation, privacy/consent, auditable publication, affordable operations, and replaceable model/storage providers;
- boundaries: CANVAS is not a general CMS, digital asset manager, image editor, marketplace, or automatic publishing engine;
- long-term success: organizations can publish trustworthy descriptions without requiring an ML team or cloud-operations staff, while blind and low-vision users remain involved in validation.

The vision must distinguish the locally runnable demo from a production-ready or fully accessibility-validated service.

## Cost principles document

`docs/cost-principles.md` will convert the existing notes into operating guidance:

- prioritize small nonprofits and predictable costs;
- retain the aspirational idle target below USD 50 per month while stating that it is neither a quote nor guarantee;
- separate fixed costs from variable costs;
- identify application hosting, database, and object storage as likely fixed categories;
- identify inference, audio generation, bandwidth, and storage growth as variable categories;
- require scale-to-zero GPU inference, generated-asset caching, private S3-compatible storage, observable per-job variable costs where practical, and avoidance of idle infrastructure whose cost dominates expected use;
- require any proposed paid managed service to document expected cost, pricing assumptions, free-tier limitations, simpler alternatives, portability/exit path, and the conditions that trigger review;
- define review triggers such as a new managed vendor, permanently running worker, material storage/egress growth, workload changes, or movement from demo to production;
- define success as an organization being able to operate CANVAS without cloud engineering expertise.

The document must not select a production vendor or describe any free tier as permanent.

## Roadmap document

`docs/roadmap.md` will be organized around current state and next milestones rather than the obsolete five-phase outline.

### Current baseline: Local MVP complete

Summarize the implemented admin-only vertical workflow: authenticated upload, zero or more ordered free-form descriptions, manual and deterministic placeholder-generated drafts, revision history, explicit approval, immutable publication snapshots, public text, cached placeholder audio, QR assets, local persistence, and automated verification. State that real model integration and full human accessibility acceptance remain incomplete.

### Milestone 1: Accessibility validation and demo hardening

Goal: close the deferred human accessibility acceptance and make demonstrations repeatable.

Deliverables include full keyboard/visible-focus, audible screen-reader, 200 percent zoom/320 CSS-pixel, contrast/non-color checks; issue remediation; supported Node 24 workflow; operator cleanup; and repeatable demo setup/reset guidance.

Success means the manual checklist is completed with recorded evidence and no unresolved release-blocking accessibility issue. This milestone does not claim formal certification.

### Milestone 2: Real caption-model integration

Goal: replace the deterministic worker internally while preserving the existing backend caption contract and editorial rules.

Deliverables include model/provider evaluation, content-safety and description-quality evaluation with blind/low-vision and editorial input, bounded asynchronous inference, cost/latency observation, optional model-heavy tests, and fallback behavior. Model output remains a draft and must never publish automatically.

Success means a selected adapter produces useful drafts under documented cost, latency, quality, privacy, and operational constraints. No specific model or GPU provider is promised in advance.

### Milestone 3: Organizational content workflows

Goal: support real organizations preparing collections of descriptions without prematurely building a public self-service platform.

Candidate deliverables include persistent users/organizations/roles, multiple administrator identities for audit, optional import of zero or more existing descriptions, artwork grouping, batch-oriented workflow aids, and access controls. Exact scope requires user research and new ADRs before implementation.

Success means a pilot organization can prepare and publish a representative collection with clear ownership and auditability.

### Milestone 4: Production readiness

Goal: validate a deployable, supportable, cost-observable service.

Deliverables include deployment evaluation, backups/restore, monitoring, secrets, retention/privacy, abuse/rate controls, security review, recovery procedures, variable-cost reporting, and a documented provider exit path.

Success means deployment and recovery are tested, expected costs are documented, operational ownership is clear, and release accessibility gates are satisfied.

### Later opportunities

List integrations, analytics, localization, CMS connectors, exhibitions/collections refinement, batch imports, and research/model improvement as non-committed opportunities. Preserve the explicit exclusions in product scope.

The roadmap will not include calendar estimates. Each milestone will contain goal, deliverables, success criteria, and explicit exclusions or decision gates.

## Project overview and architecture

`README.md` will present the current local MVP workflow accurately:

- deterministic placeholder draft generation uses submitted metadata and does not analyze images;
- the generic placeholder WAV does not narrate approved text;
- the implemented modular-monorepo topology is current rather than merely planned; and
- real caption models, GPU runtimes or providers, and text-specific audio generation remain future evaluations behind replaceable contracts.

`docs/architecture.md` will describe the implemented runtime topology and component responsibilities without incomplete headings or vendor assumptions. It will identify the backend as the authority for authentication, workflow, persistence, caption-job orchestration, assets, and publication; describe the current model-free worker; trace upload through draft, approval, asset association, and public delivery; explain PostgreSQL, private object-storage, and immutable-publication boundaries; and gate future services and provider choices on evidence and ADR review.

## Cross-document integration

The five project-facing documents will link to relevant authoritative sources:

- `README.md` for local operation and implemented status;
- `docs/product-scope.md` for MVP boundaries and exclusions;
- `docs/architecture.md` for component ownership;
- `docs/decisions/README.md` for accepted technical decisions;
- `docs/manual-testing.md` for deferred accessibility acceptance;
- the other rewritten documents where their concerns overlap.

README and architecture corrections must agree with the implemented code and configuration and with accepted ADRs 0003, 0006, and 0007. Historical ADRs remain unchanged; where an earlier record names a candidate model, current project-facing documentation follows the later accepted decision that no real model or provider is selected.

## Verification

Verification will confirm:

- no Markdown document begins with `_work in progress_`;
- the project-facing documents contain no incomplete headings or empty sections;
- relative Markdown links resolve to repository files;
- README, architecture, and roadmap current-state claims match the implemented MVP, configuration, and accepted ADRs;
- deterministic placeholder caption and generic WAV behavior are distinguished from real image analysis and narration;
- deferred work remains clearly deferred;
- cost statements agree with project cost principles and do not guarantee the aspirational target;
- accessibility wording distinguishes automated evidence, incomplete human validation, and formal certification;
- no current model, GPU provider, hosting vendor, or text-to-speech service is described as selected;
- no unresolved `TBD`, `TODO`, `FIXME`, `XXX`, WIP marker, or bare component-responsibility placeholder remains in the project-facing documents;
- `git diff --check` passes.

## Out of scope

- Market sizing, fundraising language, partnership claims, delivery dates, staffing plans, and contractual service levels.
- Changes to product scope, architecture, ADR status, or implementation.
- Changes to accepted ADR content, even where historical records name previously considered technologies.
- Formal accessibility conformance claims.
- Production vendor or model selection.
