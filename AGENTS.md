# CANVAS Agent and Contributor Guide

This file defines the working agreement for Codex and other automated or human contributors.

## Start here

Before proposing or implementing changes, read:

1. `README.md`
2. `docs/product-scope.md`
3. `docs/architecture.md`
4. `docs/cost-principles.md`
5. the relevant design or decision record

Do not infer product requirements from code alone. When code and documentation disagree, surface the conflict before silently choosing one.

## Required workflow

For non-trivial work:

1. Restate the goal and relevant constraints.
2. Propose the next step before implementation.
3. Provide a high-level explanation of the approach.
4. Identify affected modules and interfaces.
5. Write or update tests with the change.
6. Implement the smallest coherent slice.
7. Run relevant verification commands.
8. Report what was verified and what remains unverified.
9. Provide manual confirmation steps.
10. Suggest a clear commit message.

Favor small, reviewable changes over broad rewrites.

## Architectural boundaries

CANVAS is a modular application, not a distributed-systems showcase.

- `frontend/` owns browser interfaces and client-side interaction.
- `backend/` owns users, artworks, descriptions, workflow state, publishing, authorization, persistence, and job orchestration.
- `caption-worker/` owns model loading and image-caption inference.
- `infrastructure/` owns local orchestration and deployment configuration.

Do not embed JoyCaption, PyTorch, or GPU-specific logic in the Spring Boot application. The backend must depend on a captioning contract, not on a particular model implementation.

Do not split the system into additional deployable services without a demonstrated operational need.

## Product rules

- Machine-generated descriptions are drafts.
- MVP publication requires explicit human approval.
- Audio and QR assets are generated from the approved description, not the unreviewed draft.
- Jobs must be safe to retry where practical.
- Generated assets should be cached and reused.
- Publication state must be explicit and auditable.
- The MVP is admin-only unless the product scope is deliberately revised.

## Accessibility rules

Accessibility is a release criterion.

- Use semantic HTML before ARIA.
- Support keyboard-only operation.
- Preserve visible focus indicators.
- Provide meaningful labels, names, instructions, validation messages, and status announcements.
- Do not rely on color alone.
- Keep public descriptions available as text even when audio exists.
- Treat automated accessibility checks as necessary but insufficient.
- Include manual screen-reader and keyboard checks in acceptance guidance for user-facing work.

Do not use accessibility language to obscure uncertainty. Generated content must distinguish visible details from interpretation and must not invent artist intent, identity, demographic attributes, or emotional meaning.

## Artwork and content handling

CANVAS may be used for lawful artwork containing nudity, sexuality, violence, political themes, religious material, or other sensitive subject matter. Do not add arbitrary content censorship to the caption pipeline. Content policy, access controls, consent, and public presentation are separate product concerns and should be handled explicitly.

Avoid euphemistic descriptions that reduce accessibility. Descriptions should be factual, respectful, and appropriate to the approved editorial context.

## Cost and operations

The intended nonprofit operating budget is small.

- Prefer scale-to-zero GPU inference.
- Avoid permanently running GPU services.
- Avoid infrastructure whose idle cost dominates the expected budget.
- Cache generated audio, thumbnails, captions, and QR codes.
- Keep storage behind an S3-compatible abstraction.
- Make variable costs observable per job where practical.
- Do not introduce a paid managed service without documenting expected cost, free-tier assumptions, exit path, and simpler alternatives.

Never describe a free tier as a permanent production cost guarantee.

## Engineering standards

- Use supported stable language and framework versions.
- Keep configuration outside source code.
- Never commit credentials, tokens, private keys, or real user artwork without permission.
- Validate uploads by type, size, and decodeability.
- Use database migrations from the first persisted schema.
- Prefer typed API contracts.
- Handle errors explicitly and avoid leaking internal details.
- Add structured logs around asynchronous jobs and state transitions.
- Use dependency injection at external boundaries.
- Write comments for decisions and constraints, not for obvious syntax.

## Testing expectations

At minimum, new behavior should receive tests at the cheapest useful layer.

- Frontend: component and interaction tests for meaningful behavior.
- Backend: unit tests for domain logic and integration tests for persistence/API boundaries.
- Worker: unit tests for request validation and response mapping; model-heavy tests should be optional or separately marked.
- End-to-end: reserve for core workflows such as upload, review, approval, and publication.

Do not make GPU access a requirement for the default local test suite.

## Documentation and decisions

Update documentation when behavior, architecture, operating cost, or scope changes.

Store architecture decision records in `docs/decisions/`. Use the next sequential four-digit identifier and a kebab-case filename: `NNNN-short-title.md`.

Create or supersede an ADR in the same change whenever a consequential choice changes system boundaries, persistence or publication semantics, security, an external contract, deployment topology, operating-cost posture, or an accepted decision's constraints. Routine implementation details do not require an ADR.

Every ADR must include context, decision, alternatives considered, consequences, a reversal or migration path, and references. Update `docs/decisions/README.md` whenever an ADR is added or its status changes. Accepted ADRs are historical records: do not silently rewrite them; create a new ADR that supersedes the earlier record.

Avoid presenting undecided vendors as settled architecture.

## Definition of done

A change is not complete merely because code was written. It should be understandable, tested, documented where necessary, verified, and accompanied by realistic manual confirmation steps.
