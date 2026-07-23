# CANVAS roadmap

## How to read this roadmap

The local MVP is the completed baseline for this roadmap. Milestones are ordered by dependency, not promised dates. Milestone 1 is the immediate validation and hardening work; later milestones remain gated by the evidence and decisions described in each section. Later scope is subject to user research, cost review, accessibility validation, and architecture decision records (ADRs).

## Current baseline: Local MVP

CANVAS currently provides a complete, locally runnable, admin-only demonstration of the editorial publication workflow:

- session-authenticated image upload;
- zero or more ordered descriptions with free-form labels;
- manual drafts and deterministic placeholder-generated drafts;
- revision history that preserves approved wording when a new draft is created;
- explicit human approval of exact revisions;
- immutable publication snapshots containing approved descriptions in display order;
- public descriptions that remain available as text;
- cached placeholder WAV audio and QR assets associated with the published snapshot;
- local PostgreSQL and private MinIO persistence; and
- automated unit, integration, component, and end-to-end evidence for the implemented workflow.

This baseline proves the workflow without a GPU, cloud account, or paid service. The caption worker does not analyze images, and the placeholder WAV does not narrate approved text. Real caption-model integration and the full human accessibility checklist remain incomplete. Automated checks do not replace the outstanding keyboard, audible screen-reader, zoom and reflow, contrast, and non-color acceptance work.

## Milestone 1: Accessibility validation and demo hardening

### Goal

Complete the deferred human accessibility acceptance and make the local demonstration repeatable for contributors, accessibility reviewers, and potential partners.

### Deliverables

- Complete keyboard-only and human visible-focus review across the administrative and public workflows.
- Perform an audible screen-reader pass with a supported browser and assistive-technology combination.
- Validate the workflow at 200 percent zoom and at a 320 CSS-pixel viewport.
- Perform manual contrast and non-color-cue checks.
- Record issues, remediate release-blocking findings, and retest affected paths.
- Keep the standard verification workflow supported on the Node.js 24 project baseline.
- Clean up operator-facing rough edges and maintain repeatable setup, teardown, persistence-check, and confirmed local-data-reset guidance.

### Success criteria

The full [manual acceptance checklist](manual-testing.md) has recorded evidence, and no unresolved release-blocking accessibility issue remains. The documented setup and reset paths allow another operator to reproduce a clean demonstration.

### Exclusions and decision gates

This milestone does not claim formal accessibility certification or substitute automated scans for human evaluation. Any finding that changes product scope, a durable interaction contract, or an accepted architectural constraint requires the appropriate design and ADR review.

## Milestone 2: Real caption-model integration

### Goal

Replace the deterministic worker internally with a useful caption adapter while preserving the existing backend caption-job contract and the human-controlled editorial workflow.

### Deliverables

- Evaluate candidate models and execution providers without coupling the backend to one implementation.
- Evaluate description quality with blind and low-vision participants and editorial contributors.
- Review privacy, consent, content-safety behavior, and the treatment of sensitive artwork.
- Exercise bounded asynchronous inference, explicit failures, safe retries, and fallback behavior.
- Observe inference cost and latency under a documented representative workload.
- Add adapter contract coverage and keep model-heavy tests optional so the default verification suite does not require a GPU.
- Preserve generated content as an identified draft that cannot publish without explicit human approval.

### Success criteria

A selected adapter produces useful drafts under documented quality, privacy, content-safety, latency, cost, and operational constraints while retaining the current retryable job lifecycle and editorial controls.

### Exclusions and decision gates

No model, GPU host, or managed provider is selected in advance. Provider adoption requires cost review and any consequential contract, topology, security, or operating decision requires a new ADR. Model output remains draft-only and never publishes automatically.

## Milestone 3: Organizational content workflows

### Goal

Explore how real organizations prepare and govern collections of descriptions without prematurely building a public self-service platform.

### Candidate deliverables

- Persistent users, organizations, and role-based permissions.
- Multiple administrator identities so editorial and publication actions remain attributable.
- Optional import of zero or more existing descriptions into the revisioned workflow.
- Artwork grouping for organizational use.
- Batch-oriented workflow aids.
- Access controls that reflect validated ownership and collaboration needs.

### Success criteria

A pilot organization can prepare and publish a representative collection with clear ownership, attributable editorial actions, and auditable publication history.

### Exclusions and decision gates

The candidate scope requires user research before commitment. The identity model, organization boundaries, audit semantics, import behavior, grouping concepts, and access-control design require new ADRs before implementation. This milestone does not promise a public artist portal or a particular role model.

## Milestone 4: Production readiness

### Goal

Validate that CANVAS can be deployed, recovered, secured, supported, and operated with observable costs while satisfying its accessibility release criteria.

### Deliverables

- Evaluate deployment options against operational, portability, accessibility, and cost constraints.
- Define and test backup, restore, deployment, and recovery procedures.
- Add production-appropriate monitoring, structured operational signals, and actionable failure handling.
- Establish secrets management and configuration practices.
- Define retention, privacy, consent, and deletion policies for artwork and generated assets.
- Add proportionate abuse prevention and rate controls.
- Conduct security review and remediate release-blocking findings.
- Report variable costs and document the assumptions behind expected operating costs.
- Document provider and data exit paths.
- Satisfy the human accessibility release gates for the production configuration.

### Success criteria

Deployment and recovery procedures are tested, expected costs and assumptions are documented, operational ownership is clear, security findings are addressed to the agreed release threshold, and accessibility release gates are satisfied.

### Exclusions and decision gates

This milestone does not promise a specific host, managed service, monthly cost, service-level agreement, or certification. Production providers and operational commitments remain subject to cost, security, privacy, accessibility, and ADR review.

## Later opportunities

The following are non-committed opportunities for research and prioritization after the preceding evidence exists:

- CMS and museum-system integrations;
- privacy-reviewed analytics;
- localization;
- richer exhibition and collection workflows;
- batch imports and related migration tools; and
- description research, model evaluation, and model improvement.

These opportunities do not expand current product scope or bypass the research, accessibility, cost, and ADR gates above.

## Explicitly out of scope

CANVAS does not plan to provide:

- NFT support;
- a marketplace;
- a social network;
- image editing;
- general-purpose digital asset management; or
- general CMS replacement.

## Related documents

- [Project overview and local operation](../README.md)
- [Product vision](../VISION.md)
- [Product scope](product-scope.md)
- [Architecture](architecture.md)
- [Cost principles](cost-principles.md)
- [Manual accessibility acceptance](manual-testing.md)
- [Architecture decision records](decisions/README.md)
