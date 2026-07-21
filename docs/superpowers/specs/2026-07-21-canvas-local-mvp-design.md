# CANVAS Local MVP Design

**Status:** Approved for implementation planning

**Date:** 2026-07-21

## Goal

Build a locally runnable demonstration of the complete CANVAS editorial workflow. An administrator can upload artwork, create or generate one or more descriptions, review and approve them, publish an accessible public artwork page, play cached placeholder audio, and download a QR code. The demo requires no GPU, cloud account, or paid service.

## Scope and authority

This design refines the MVP defined by `docs/product-scope.md` and follows the accepted decisions in `docs/decisions/0001-project-foundation.md`. Documents beginning with `_work in progress_`, including `VISION.md`, `docs/cost-principles.md`, and `docs/roadmap.md`, are directional context rather than settled requirements.

The MVP is admin-only. It excludes public registration, artist accounts, organization management, role-based permissions, bulk upload, collections, search, analytics, localization, public APIs, museum integrations, model training, and real model inference.

## Delivery approach

Implementation will proceed as vertical workflow milestones. Each milestone will add the smallest coherent behavior across the user interface, API, persistence, and tests, leaving a demonstrable system rather than completing one technical layer at a time.

## System architecture

CANVAS is a modular monorepo with four primary boundaries:

- `frontend/` owns the React and TypeScript administration interface and public artwork pages.
- `backend/` is a Spring Boot modular monolith. It owns authentication, artwork metadata, workflow rules, description revisions, approval, publication, persistence, job orchestration, QR generation, and asset URLs.
- `caption-worker/` is a Python HTTP service implementing the captioning contract. In this MVP it returns deterministic draft text and does not load a machine-learning model.
- `infrastructure/` owns Docker Compose and local service configuration.

PostgreSQL stores application state. MinIO provides local S3-compatible object storage for artwork and generated assets. The backend is the sole authority for workflow transitions. The browser and caption worker cannot approve or publish content.

Audio generation is behind a backend interface. The MVP adapter produces a deterministic, valid placeholder audio asset and reuses it when its approved input has not changed. This proves orchestration, caching, storage, and playback without selecting a production text-to-speech provider.

Docker Compose runs PostgreSQL, MinIO, the backend, the frontend, and the placeholder caption worker. Developers may also run application components directly while retaining the containerized dependencies.

## Domain model

### Artwork

An `Artwork` records:

- title;
- artist or display credit;
- optional editorial context;
- original image object reference and validated media metadata;
- workflow and publication status;
- public slug when published;
- optimistic-lock version;
- creation and update audit data.

An artwork can exist with zero descriptions. Caption generation is optional.

### Description

An `Artwork` owns zero or more `Description` records. Each description records:

- a user-supplied label, such as `Objective` or `Subjective`;
- stable display order within the artwork;
- source, either `MANUAL` or `GENERATED`;
- editable draft text;
- lifecycle state, either `DRAFT` or `APPROVED`;
- revision lineage;
- approval identity and timestamp when approved;
- creation and update audit data.

Description labels are free-form organization terminology. They are not hard-coded columns or enum values. Generated and manually authored descriptions follow the same review and approval rules.

Editing an approved description creates a new draft revision. It never mutates text that has already been approved or published. Previously published content therefore remains auditable until a new revision is explicitly approved and republished.

### Generated assets

Generated asset records associate an asset with its input revision and storage object. Audio is associated with an approved description revision. The QR code is associated with the artwork's stable public URL. Matching assets are reused; changed inputs create replacement assets without losing audit history.

## Workflow rules

The administrator can use either editorial path:

1. Upload artwork, manually add one or more labeled descriptions, approve them, and publish.
2. Upload artwork, request a deterministic placeholder-generated description, edit it, approve it, and publish.

Caption generation creates a new `GENERATED` draft description. It does not overwrite another description or occupy a special field on `Artwork`.

Publication requires at least one approved description. The backend enforces this invariant. Publishing ensures the public representation, placeholder audio for approved descriptions, and QR asset exist before marking the artwork published. Public endpoints expose only explicitly published artwork and approved description revisions.

Transition requests are idempotent where practical. Repeated caption, audio, QR, or publication requests either return an existing matching result or safely resume incomplete work.

## User experience

### Administration

The admin interface supports:

1. Signing in with the single configured administrator account.
2. Viewing artworks with title, workflow status, and the next available action.
3. Uploading one image with title, artist or display credit, and optional context.
4. Adding, labeling, ordering, editing, and approving manual descriptions.
5. Requesting a placeholder-generated description and observing pending, completed, or failed status.
6. Reviewing and editing generated descriptions with clear draft labeling.
7. Approving individual description revisions through an explicit confirmation.
8. Publishing an artwork when at least one description is approved.
9. Opening the public page and downloading its QR image.

Asynchronous caption work exposes a pollable status. The MVP does not add WebSockets.

### Public artwork page

The public page contains:

- artwork title and display credit;
- the artwork image with suitable alternative text;
- every approved published description, labeled and ordered;
- audio controls for cached placeholder audio;
- the same descriptions as visible text transcripts.

The page contains no administrative controls or draft content.

## API design

The REST API is organized around resources and explicit transitions rather than a generic state mutation endpoint. It provides operations for:

- authentication and logout;
- artwork listing, upload, detail, and metadata editing;
- description creation, editing, ordering, and approval;
- caption request creation and status retrieval;
- artwork publication;
- public artwork retrieval;
- QR retrieval or download;
- authorized asset access and public published-asset access.

API contracts use typed request and response models. Error responses use a stable problem-details shape and do not expose stack traces, credentials, storage keys, or infrastructure details.

## Authentication and authorization

Spring Security protects every administrative endpoint. The MVP has one administrator whose username and encoded password are supplied through environment configuration. It has no registration, invitation, password-reset, or role-management flows.

Browser authentication uses secure, HTTP-only session cookies. State-changing requests receive CSRF protection. Public endpoints return only published representations and their public assets.

## Validation and failure handling

Uploads have configurable size limits and a media-type allowlist. The backend verifies that an uploaded file is a decodable supported image. Original filenames do not determine object-storage keys.

Failures behave as follows:

- If the caption worker is unavailable, the caption request becomes failed and retryable; artwork and manual descriptions remain intact.
- If object storage fails during upload, the backend does not commit metadata pointing to a missing object.
- If placeholder audio or QR generation fails, the artwork remains approved but unpublished and publication can be retried.
- Duplicate transition requests return an existing result when the request is already satisfied.
- Stale edits fail through optimistic locking rather than overwriting newer changes.
- Publication with no approved description fails with an actionable validation response.
- Unexpected failures are logged with structured context and returned without internal implementation details.

## Accessibility requirements

Accessibility is a release criterion for both admin and public experiences.

- Use semantic HTML before ARIA.
- Support keyboard-only operation and preserve visible focus indicators.
- Give controls meaningful accessible names and associated instructions.
- Present actionable validation errors and move or associate focus appropriately.
- Announce asynchronous caption and publication status changes.
- Do not communicate workflow state through color alone.
- Keep every public description available as visible text when audio exists.
- Clearly distinguish generated drafts from approved content.
- Preserve description labels and ordering in both visual and assistive-technology reading order.

Automated accessibility tests are necessary but do not replace manual keyboard and screen-reader checks.

## Testing strategy

Each vertical milestone receives tests at the cheapest useful boundary:

- Backend unit tests cover description revisions, approval, publication eligibility, idempotency, optimistic locking, and adapter behavior.
- Backend integration tests cover PostgreSQL persistence, migrations, MinIO-backed uploads, authentication, authorization, and REST contracts.
- Caption-worker unit tests cover request validation and deterministic response mapping without model dependencies.
- Frontend component and interaction tests cover forms, multiple descriptions, ordering, confirmation, status changes, failures, keyboard use, and accessible names.
- A small end-to-end suite covers the manual-description path and the placeholder-generated-description path through publication.
- Automated accessibility checks run on the principal admin and public pages.
- Manual acceptance guidance covers keyboard-only operation and a screen-reader pass.

GPU access is never required by the default test suite.

## Local operation

A new checkout can be configured with documented environment values and started through Docker Compose. Startup creates or verifies MinIO storage resources and applies versioned database migrations. PostgreSQL and MinIO use named volumes so application data and cached assets survive ordinary stack restarts.

The local setup documents health checks, seeded administrator configuration, supported upload limits, service URLs, test commands, and a reset procedure. No committed configuration contains real credentials or user artwork.

## Completion criteria

The local MVP is complete when a new checkout can:

1. Start the full stack without a GPU, cloud account, or paid service.
2. Sign in using environment-configured administrator credentials.
3. Upload and persist a valid artwork image and metadata.
4. Add, label, order, edit, and approve multiple manual descriptions.
5. Alternatively request, edit, and approve a deterministic placeholder-generated description.
6. Reject publication until at least one description is approved.
7. Publish an accessible public page containing only approved description revisions.
8. Generate and reuse valid placeholder audio assets and a QR code.
9. Play audio, read equivalent visible text, and download the QR image.
10. Preserve application data and cached assets across an ordinary stack restart.
11. Pass the documented unit, integration, frontend, worker, end-to-end, and automated accessibility checks.
12. Complete the documented manual keyboard and screen-reader acceptance checks.

## Deferred decisions

The MVP deliberately defers:

- real JoyCaption or other model integration;
- production text-to-speech selection;
- production hosting and managed-service selection;
- organization tenancy and role-based permissions;
- organization-level description templates or controlled label vocabularies;
- public artist accounts and direct artist submission;
- collections, exhibitions, search, analytics, localization, and external integrations.

The caption-worker and audio interfaces, multi-description domain model, S3-compatible storage boundary, and modular backend keep these additions possible without treating undecided vendors or future features as current architecture.
