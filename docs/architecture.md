# CANVAS architecture

## Purpose and current status

CANVAS is an editorial publication platform for creating, reviewing, approving, and publishing visual descriptions of artwork. Machine-generated content is optional draft material; the backend, not a caption engine, remains the authority for approval and publication.

The implemented system is an admin-only local MVP. It proves the workflow with a deterministic, model-free caption worker that uses submitted artwork metadata and explicitly does not analyze image content. Its audio generator returns a generic checked-in placeholder WAV that does not narrate approved text. A real caption model, GPU runtime or provider, production deployment, and text-specific audio generation remain future work.

## Design principles

- Accessibility and explicit human approval are release requirements.
- The Spring Boot backend owns workflow state, authorization, persistence, job orchestration, assets, and publication.
- Caption execution and object storage remain replaceable behind explicit contracts.
- The default local and automated test paths require no GPU, cloud account, or paid service.
- Generated assets are cached and tied to exact publication inputs and associations.
- Additional deployable services require demonstrated operational need.
- Production infrastructure choices must remain affordable and portable for small nonprofits.

## Implemented topology

```text
Browser
  |
  | same-origin administration and public routes
  v
React + TypeScript frontend
  |
  | REST APIs, session cookie, CSRF protection
  v
Spring Boot modular-monolith backend
  |                  |                         |
  | JPA/Flyway       | S3-compatible API       | typed HTTP caption contract
  v                  v                         v
PostgreSQL      private object storage     FastAPI caption worker
               - artwork originals        deterministic metadata response
               - generated assets         no image model or GPU
```

Docker Compose supplies the local topology: frontend, backend, caption worker, PostgreSQL, MinIO, and a one-shot MinIO initializer. Published host ports bind to loopback. PostgreSQL and MinIO use named volumes so ordinary container stops and teardowns preserve local application data.

## Component responsibilities

### `frontend/`

The React application owns browser presentation and interaction. It provides the configured-administrator sign-in flow, artwork upload and editing views, manual and placeholder-draft interactions, revision approval and ordering controls, publication controls, and the public artwork page. It renders published descriptions as text even when an audio control is present and obtains all authoritative state through backend APIs.

The frontend does not decide whether a revision is approved or an artwork is publishable. Those rules are enforced again by the backend.

### `backend/`

The Spring Boot application is a modular monolith with enforced module boundaries:

- `identity` configures the single local administrator, session authentication, CSRF protection, and public-route exceptions;
- `artwork` validates PNG/JPEG uploads by declared type, configured size, and successful decode, stores originals, and persists artwork metadata;
- `description` owns ordered descriptions, manual and generated sources, revision history, optimistic concurrency, and explicit approval;
- `caption` owns persistent job state, bounded asynchronous execution, restart recovery, retry behavior, the worker client contract, and conversion of a successful result into an unapproved generated draft;
- `publication` snapshots approved revisions in display order, creates or reuses associated assets, marks one snapshot current, and serves only the current public representation;
- `storage` implements the private S3-compatible object-storage boundary; and
- `shared` contains cross-cutting health and API error handling.

PostgreSQL is the backend's authoritative store for artwork metadata, description revisions, caption jobs, publication snapshots, generated-asset metadata, and associations. Flyway owns forward-only schema migration. The backend stores binary originals and generated assets through object storage rather than in database blobs.

### `caption-worker/`

The FastAPI worker exposes readiness and caption endpoints defined by typed request and response models. The current adapter validates submitted metadata and returns deterministic demo text labeled with engine `deterministic-placeholder` and version `1`. It does not fetch or inspect the supplied image URL, load a visual-language model, require a GPU, or provide production-quality description inference.

The worker does not approve, persist, or publish descriptions. Replacing it with a real model adapter must preserve the backend contract and the rule that every generated result enters the description workflow as a draft.

### Storage and infrastructure

PostgreSQL persists application and audit state. MinIO is the local implementation of the S3-compatible storage contract, with separate private buckets for artwork originals and generated assets. MinIO root credentials are limited to service bootstrap and the one-shot initializer; the backend uses a distinct application identity scoped to those buckets.

`compose.yaml` coordinates the local runtime and health dependencies. `infrastructure/` contains the MinIO bucket and policy initialization plus its configuration test. Production infrastructure is not selected by this topology.

## Workflow and data flow

1. A configured administrator authenticates with the backend and uploads a PNG or JPEG plus title, credit, and optional context. The backend validates and decodes the image, stores its bytes in the private originals bucket, and records metadata in PostgreSQL.
2. The administrator either creates a manual draft or requests placeholder generation. For generation, the backend persists a caption job, runs it on a bounded executor, and calls the worker with artwork metadata and an image URL. The current worker uses only the metadata and returns text that states no image content was analyzed.
3. A successful caption job creates a `GENERATED` draft in the same revisioned description workflow used by manual content. Failures retain a safe public error and can be retried; incomplete jobs are recovered after backend startup.
4. The administrator edits, orders, and explicitly approves exact description revisions. Creating a new draft after approval preserves the approved revision in history. Generated source never bypasses this approval boundary.
5. Publication is rejected when no approved description exists. Otherwise, the backend snapshots the latest approved revision of each description in display order, creates or reuses one audio asset per approved input and one QR asset for the stable public URL, associates those exact assets with the snapshot, and only then marks the publication current.
6. Public routes serve the current snapshot's artwork metadata, approved text, image, associated audio URLs, and QR download. The current WAV is only a generic placeholder; the approved text on the page is the accessible description. Asset URLs include generated-asset identifiers and are authorized through the current publication snapshot.

## Persistence, storage, and publication boundaries

- PostgreSQL records workflow truth and object references; the object store records binary bytes.
- Original artwork and generated assets occupy separate private buckets and are read through backend-controlled routes.
- Generated audio identity derives from the approved revision identifier, label, exact text, and generator namespace. QR identity derives from the exact public URL and generator namespace.
- Generated metadata and objects are reused by deterministic content identity. If metadata exists but an object is missing, the backend can repair the same object key without creating duplicate metadata.
- A publication is an immutable snapshot of approved wording, order, artwork metadata, and exact asset associations. A later publication supersedes the prior current snapshot rather than mutating its historical content.
- Public generated-asset responses use identifier-bearing URLs and immutable cache headers. A URL associated only with a superseded snapshot may return `404` under the current authorization rule.

These boundaries are detailed in [ADR 0005](decisions/0005-revisioned-description-and-publication-model.md), [ADR 0006](decisions/0006-replaceable-caption-job-contract.md), and [ADR 0007](decisions/0007-immutable-generated-assets-and-storage.md).

## Future evolution gates

A real caption model, GPU execution environment or provider, and text-specific audio generator must be evaluated rather than assumed. Candidate integrations must fit the existing caption and audio contracts or introduce an explicitly versioned migration, preserve draft-only generated content, keep default tests model-free, and document quality, accessibility, privacy, security, latency, cost, and recovery behavior. No model or provider is selected by the current architecture.

Search, analytics, organization workflows, model training, or any other new deployable service should be added only after user research and operational evidence demonstrate the need. Consequential changes to topology, contracts, persistence, publication semantics, or recurring cost require an architecture decision record and a documented reversal or migration path.

## Related documents

- [Project overview and local operation](../README.md)
- [Product scope](product-scope.md)
- [Cost principles](cost-principles.md)
- [Roadmap](roadmap.md)
- [Architecture decision records](decisions/README.md)
- [Local MVP design](superpowers/specs/2026-07-21-canvas-local-mvp-design.md)
