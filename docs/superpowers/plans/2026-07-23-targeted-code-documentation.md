# Targeted Code Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clarify CANVAS contracts and non-obvious invariants with targeted JavaDoc, TSDoc, Python docstrings, SQL/shell comments, and durable contributor guidance without changing behavior.

**Architecture:** Document module boundaries and external ports first, then lifecycle-heavy backend logic, browser/worker contracts, and persistence/bootstrap safety rules. Every comment must be validated against current implementation and tests; comments that only narrate syntax or control flow are defects.

**Tech Stack:** Java 21 JavaDoc, TypeScript/TSDoc, Python 3.13 docstrings, PostgreSQL SQL comments, POSIX shell comments, Maven, npm, pytest.

## Global Constraints

- This is documentation-only: do not change runtime behavior, public APIs, schemas, dependencies, configuration, generated assets, or test assertions.
- Document contracts, invariants, side effects, idempotency/retry behavior, error guarantees, and security/accessibility constraints only when they are not obvious from types and names.
- Explain why and what callers may rely on; do not narrate syntax, method names, ordinary control flow, getters, repository conventions, JSX structure, or simple controller delegation.
- Do not comprehensively document every public/exported symbol.
- Do not add JavaDoc/TSDoc lint dependencies or generated-document publishing.
- Keep comments consistent with ADRs 0003–0007 and current tests.
- Run Node commands with `/home/charlie/.nvm/versions/node/v24.18.0/bin` first on `PATH`.
- Do not modify the flaky `PublishPanel` focus test or behavior; report a recurrence separately.

---

### Task 1: Establish the documentation convention and module-facing contracts

**Files:**
- Modify: `AGENTS.md`
- Modify: `backend/src/main/java/me.acharliekelly.canvas/artwork/package-info.java`
- Modify: `backend/src/main/java/me.acharliekelly.canvas/caption/package-info.java`
- Modify: `backend/src/main/java/me.acharliekelly.canvas/description/package-info.java`
- Modify: `backend/src/main/java/me.acharliekelly.canvas/identity/package-info.java`
- Modify: `backend/src/main/java/me.acharliekelly.canvas/publication/package-info.java`
- Modify: `backend/src/main/java/me.acharliekelly.canvas/shared/package-info.java`
- Modify: `backend/src/main/java/me.acharliekelly.canvas/storage/package-info.java`
- Modify: `backend/src/main/java/me.acharliekelly.canvas/storage/ObjectStorage.java`
- Modify: `backend/src/main/java/me.acharliekelly.canvas/caption/CaptionClient.java`
- Modify: `backend/src/main/java/me.acharliekelly.canvas/publication/asset/AudioGenerator.java`
- Modify: `backend/src/main/java/me.acharliekelly.canvas/publication/asset/QrCodeGenerator.java`

**Interfaces:**
- Produces: the targeted documentation standard used by Tasks 2–4.
- Produces: module ownership descriptions and caller-visible port guarantees.

- [ ] **Step 1: Add targeted code-documentation rules to `AGENTS.md`**

Under Engineering standards, add a `### Code documentation` subsection containing these requirements in polished prose:

```markdown
- Document module-facing contracts and non-obvious invariants, side effects, retry/idempotency rules, error guarantees, and security or accessibility constraints.
- Use JavaDoc for consequential Java contracts, TSDoc for consequential exported TypeScript contracts, Python docstrings for worker contracts, and language-native comments for SQL and shell invariants.
- Explain why a constraint exists and what callers may rely on. Do not restate syntax, names, ordinary control flow, getters, repository conventions, JSX structure, or simple controller delegation.
- Update documentation in the same change when documented behavior changes. Prefer clearer structure over comments when structure is the source of confusion.
```

Preserve the existing rule that comments explain decisions and constraints rather than obvious syntax.

- [ ] **Step 2: Document Spring Modulith package ownership**

Add package JavaDoc immediately before each package declaration/annotation. Each comment must identify ownership and boundary, for example:

```java
/**
 * Owns artwork metadata, validated original-image ingestion, and artwork lifecycle state.
 * Other modules use the artwork API or repository-visible domain contract rather than taking
 * ownership of object-storage validation and compensation.
 */
@ApplicationModule
package me.acharliekelly.canvas.artwork;
```

Use accurate equivalents for:

- caption: persistent orchestration and replaceable worker contract, never model implementation;
- description: ordered descriptions, editable current drafts, and retained immutable approved revisions;
- identity: configured administrator and session/CSRF security boundary;
- publication: immutable snapshots, public reads, and generated-asset association;
- shared: cross-module error/readiness primitives only;
- storage: private S3-compatible object operations, independent of domain workflow.

Do not claim package encapsulation stronger than the current code enforces.

- [ ] **Step 3: Document `ObjectStorage`**

Add interface and method JavaDoc that states:

- instances are qualified for either originals or generated assets and operate within one configured private bucket;
- `put`/`putGenerated` consume caller-owned streams without promising to close them;
- `get` returns a stream the caller must close;
- `head` returns empty only for a missing object and propagates other storage failures;
- `delete` is safe when the key is already absent;
- `StoredObject` describes the exact persisted key used for compensation and repair confirmation; `ObjectMetadata` supplies byte count and media type for cache reuse and repair validation.

Use `@param`, `@return`, and `@throws` only where they add these non-obvious guarantees.

- [ ] **Step 4: Document caption/audio/QR ports**

Add JavaDoc with these exact semantics:

- `CaptionClient.caption`: performs one worker call; does not retry; transport/contract failures propagate to job orchestration; returned text is draft material with engine provenance.
- `AudioGenerator.generate`: accepts an exact approved revision identity/label/text; output metadata identifies generator compatibility; deterministic cache identity is handled by `AssetService`, not promised solely by bytes.
- `ApprovedDescriptionInput`: revision ID, label, and text jointly identify the publication input.
- `GeneratedBinary`: byte array, media type, and generator string are a compatibility tuple; callers must not mutate the returned byte array.
- `QrCodeGenerator.generate`: encodes the exact supplied public URI and returns generator/media metadata used by cache repair validation.

Avoid claims that the placeholder WAV narrates its input.

- [ ] **Step 5: Verify and commit Task 1**

Run:

```bash
cd backend && ./mvnw -q -DskipTests compile
cd backend && ./mvnw -q -DskipTests javadoc:javadoc
rg -n "Code documentation|caller must close|does not retry|approved revision|exact supplied public URI" AGENTS.md backend/src/main/java
! rg -n "TODO|FIXME|TBD|XXX" AGENTS.md backend/src/main/java
git diff --check
```

Expected: Java compilation and JavaDoc generation succeed; targeted guarantees are discoverable; no placeholders or whitespace errors exist.

Commit:

```bash
git add AGENTS.md backend/src/main/java/me.acharliekelly.canvas/*/package-info.java \
  backend/src/main/java/me.acharliekelly.canvas/storage/ObjectStorage.java \
  backend/src/main/java/me.acharliekelly.canvas/caption/CaptionClient.java \
  backend/src/main/java/me.acharliekelly.canvas/publication/asset/AudioGenerator.java \
  backend/src/main/java/me.acharliekelly.canvas/publication/asset/QrCodeGenerator.java
git commit -m "docs: describe module and port contracts"
```

---

### Task 2: Document backend lifecycle, audit, and concurrency invariants

**Files:**
- Modify: `backend/src/main/java/me.acharliekelly.canvas/artwork/ArtworkService.java`
- Modify: `backend/src/main/java/me.acharliekelly.canvas/description/DescriptionService.java`
- Modify: `backend/src/main/java/me.acharliekelly.canvas/caption/CaptionJobService.java`
- Modify: `backend/src/main/java/me.acharliekelly.canvas/caption/CaptionJobRunner.java`
- Modify: `backend/src/main/java/me.acharliekelly.canvas/publication/PublicationService.java`
- Modify: `backend/src/main/java/me.acharliekelly.canvas/publication/asset/AssetService.java`
- Modify: `backend/src/main/java/me.acharliekelly.canvas/caption/api/CaptionJobResponse.java`

**Interfaces:**
- Consumes: the targeted documentation standard from Task 1.
- Produces: accurate lifecycle and concurrency guidance adjacent to the implementation it constrains.

- [ ] **Step 1: Document upload and description revision semantics**

In `ArtworkService`, document the upload entry point and compensation block:

- validation and image decode occur before object storage;
- the original object is stored before the database row;
- a persistence failure triggers best-effort object deletion without exposing the key;
- storage cleanup failure is logged but does not replace the original error.

In `DescriptionService`, document:

- every save after an approved revision appends a new draft rather than mutating approved history;
- approval applies only to the current saved draft and records administrator/time;
- artwork row locking and optimistic version checks serialize mutations and advance the artwork version consumed by publication;
- reorder temporarily offsets all display positions before writing final positions to avoid the unique `(artwork_id, display_order)` constraint during swaps.

Use one short comment at the two-phase reorder query sequence; do not narrate each update.

- [ ] **Step 2: Document caption job orchestration**

In `CaptionJobService` and `CaptionJobRunner`, document:

- one active job per artwork is enforced by persistence identity/state, while completed results remain queryable;
- request creation is separate from asynchronous execution;
- claim, external call, success finalization, and failure finalization use separate transactions so network latency does not hold database locks;
- startup recovery requeues pending/running work without changing the request/retry attempt ordinal;
- retries reuse a job safely and never publish or approve generated text;
- success creates a generated draft and retains its result link even after the job is terminal.

Add JavaDoc to `CaptionJobResponse` only for fields whose polling semantics are non-obvious: terminal state, attempt count, result description, and sanitized error message.

- [ ] **Step 3: Document publication snapshot and authorization semantics**

Add JavaDoc to `PublicationService.publish` and public read methods, plus focused inline comments:

- publication locks/checks the artwork version and selects the latest approved revision for each ordered description;
- zero approved descriptions is rejected;
- content hash uses length-prefixed strings, revision UUIDs, order, artwork image identity, title, and credit to avoid ambiguous concatenation;
- equality is idempotent only against the current snapshot; A-to-B-to-A creates a third immutable audit event;
- assets are associated with the exact snapshot before the previous snapshot is superseded/current state changes;
- public image/audio/QR reads authorize only against the current publication and exact persisted asset IDs, so superseded URLs may return not found;
- `PublicationResult.created` distinguishes a new audit event from current-snapshot reuse, and `qrUrl` is versioned by generated asset ID.

Add a short comment around `contentHash` framing rather than comments on every digest update.

- [ ] **Step 4: Document generated-asset caching and transaction locking**

Add class/method JavaDoc and concise comments in `AssetService` explaining:

- input keys include kind, generator namespace, and exact input identity;
- the in-memory keyed lock serializes same-process creators only and is not a distributed lock;
- the lock is deliberately held until transaction completion so rollback compensation finishes before a waiter can reuse the key;
- cache hits verify object size/media type, and missing/mismatched objects are regenerated only when the active input/generator configuration can reproduce compatible metadata;
- storage puts register key-based rollback deletion; cache hits do not register ownership or compensation;
- a cross-process uniqueness race propagates and runs its registered key-based rollback compensation, without cross-process storage isolation;
- lock entries use reference counts so entries are removed only after the last holder/waiter completes.

Preserve and refine the existing rollback/waiter comment rather than duplicating it.

- [ ] **Step 5: Verify and commit Task 2**

Run:

```bash
cd backend && ./mvnw test -Dtest=ArtworkServiceTest,DescriptionServiceTest,CaptionJobServiceTest,PublicationServiceTest,AssetServiceTest
cd backend && ./mvnw -q -DskipTests javadoc:javadoc
rg -n "best-effort|append|separate transaction|A-to-B-to-A|current publication|not a distributed lock|transaction completion|reference count" backend/src/main/java
! rg -n "TODO|FIXME|TBD|XXX" backend/src/main/java
git diff --check
```

Expected: focused backend tests and JavaDoc generation pass; lifecycle guarantees are present and placeholder scans are clean.

Commit:

```bash
git add backend/src/main/java/me.acharliekelly.canvas/artwork/ArtworkService.java \
  backend/src/main/java/me.acharliekelly.canvas/description/DescriptionService.java \
  backend/src/main/java/me.acharliekelly.canvas/caption/CaptionJobService.java \
  backend/src/main/java/me.acharliekelly.canvas/caption/CaptionJobRunner.java \
  backend/src/main/java/me.acharliekelly.canvas/caption/api/CaptionJobResponse.java \
  backend/src/main/java/me.acharliekelly.canvas/publication/PublicationService.java \
  backend/src/main/java/me.acharliekelly.canvas/publication/asset/AssetService.java
git commit -m "docs: explain backend lifecycle invariants"
```

---

### Task 3: Document browser and caption-worker contracts

**Files:**
- Modify: `frontend/src/api/client.ts`
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/artworks/DescriptionEditor.tsx`
- Modify: `frontend/src/artworks/CaptionRequestPanel.tsx`
- Modify: `frontend/src/artworks/PublishPanel.tsx`
- Modify only if needed: `frontend/src/auth/SessionProvider.tsx`
- Modify: `caption-worker/src/canvas_caption_worker/contracts.py`
- Modify: `caption-worker/src/canvas_caption_worker/main.py`

**Interfaces:**
- Consumes: backend session, revision, caption-job, publication, and public-snapshot behavior documented in Tasks 1–2.
- Produces: TSDoc/docstrings and state-invariant comments for browser and worker consumers.

- [ ] **Step 1: Document `apiFetch` and typed API semantics**

Add TSDoc to `apiFetch` stating:

- requests use same-origin session credentials;
- mutation requests obtain the current CSRF token from `/api/session` and add `X-CSRF-TOKEN`;
- successful empty responses produce `undefined`, otherwise JSON is returned as the requested type;
- RFC problem JSON becomes an `ApiError` exposing status/code/field, while non-problem failures use a safe fallback message;
- requests are not automatically retried.

In `api/types.ts`, add targeted TSDoc to the interface/group level for:

- `DescriptionResponse`: current revision plus retained immutable approved history; `approvedRevisionId` may name approved history while the current unapproved draft is editable; `version` is optimistic concurrency;
- `CaptionJobResponse`: polling lifecycle, terminal states, attempt count, optional result, sanitized error;
- `PublicationResponse`: `created`, returned artwork version, current snapshot content, and versioned `qrUrl`;
- `PublicArtwork`: current immutable snapshot only; drafts/admin metadata excluded; audio URLs may be null for conservatively upgraded legacy snapshots.

Do not add comments to obvious scalar fields.

- [ ] **Step 2: Document complex editor/polling/publication state**

Add short comments/TSDoc that explain:

- `knownDescriptionIds` prevents repeated completed-job polling responses from incrementing artwork version twice;
- initial generated results create unapproved drafts; later redelivery replaces the same description by ID with its current server state, which may have since been approved;
- approval-dialog dismissal restores focus to its trigger, with status fallback if the trigger disappeared;
- dirty local edits disable approval because approval must target the saved revision;
- caption polling has one timer owner, stops on terminal state/unmount, and may safely redeliver a result;
- publication previews use only approved/current approved-history entries in display order;
- publish responses can be idempotent and must adopt the server-returned artwork version/URLs;
- focus restoration occurs only after dialog completion, not during the publishing render.

Do not comment JSX layout, state setters whose purpose is obvious, or every promise branch.

- [ ] **Step 3: Document the Python worker contract**

Add module/class/function docstrings stating:

- contract models forbid unknown fields and normalize required metadata;
- `imageUrl` is syntactically validated but the placeholder does not fetch, decode, or inspect it;
- context trims to `None` when blank;
- engine/version identify deterministic placeholder provenance;
- `/health` indicates process readiness only;
- `/captions` returns metadata-derived draft text and does no image inference.

Use concise docstrings such as:

```python
async def caption(request: CaptionRequest) -> CaptionResponse:
    """Return deterministic draft text from metadata without fetching or analyzing the image."""
```

- [ ] **Step 4: Verify and commit Task 3**

Run:

```bash
cd frontend && PATH=/home/charlie/.nvm/versions/node/v24.18.0/bin:$PATH npm run lint
cd frontend && PATH=/home/charlie/.nvm/versions/node/v24.18.0/bin:$PATH npm run typecheck
cd frontend && PATH=/home/charlie/.nvm/versions/node/v24.18.0/bin:$PATH npm test -- --run
cd caption-worker && python -m pytest -q
rg -n "CSRF|not automatically retried|immutable approved|redeliver|restore focus|does not fetch|process readiness" frontend/src caption-worker/src
! rg -n "TODO|FIXME|TBD|XXX" frontend/src caption-worker/src
git diff --check
```

Expected: lint, typecheck, 51 frontend tests, and 6 worker tests pass; targeted contract language is present.

If the known `PublishPanel` focus test fails once but passes on immediate focused and full reruns, report it without modifying behavior or tests.

Commit:

```bash
git add frontend/src/api/client.ts frontend/src/api/types.ts \
  frontend/src/artworks/DescriptionEditor.tsx frontend/src/artworks/CaptionRequestPanel.tsx \
  frontend/src/artworks/PublishPanel.tsx frontend/src/auth/SessionProvider.tsx \
  caption-worker/src/canvas_caption_worker/contracts.py \
  caption-worker/src/canvas_caption_worker/main.py
git commit -m "docs: clarify browser and worker contracts"
```

---

### Task 4: Document migration and MinIO bootstrap safety

**Files:**
- Modify: `backend/src/main/resources/db/migration/V3__description_revision_ownership.sql`
- Modify: `backend/src/main/resources/db/migration/V5__retain_caption_job_results.sql`
- Modify: `backend/src/main/resources/db/migration/V8__publication_asset_associations.sql`
- Modify: `infrastructure/minio/create-bucket.sh`

**Interfaces:**
- Consumes: revision, caption-result, publication-asset, and storage credential invariants documented in prior tasks.
- Produces: adjacent operational commentary for irreversible migrations and repeat-safe private-bucket bootstrap.

- [ ] **Step 1: Add migration rationale comments**

Use `--` comments to explain only these non-obvious choices:

- V3: ownership columns/backfill allow the current revision and its description to be constrained consistently after existing data is migrated; ordering prevents foreign-key validation from failing;
- V5: result-description ownership is intentionally retained when a caption job reaches terminal state so successful generated drafts remain auditable/queryable;
- V8: dropping historical artwork/content uniqueness allows A-to-B-to-A to create a new audit event; audio/QR backfills associate only an unambiguous single candidate; ambiguous legacy rows stay nullable for text-first public compatibility; `ON DELETE RESTRICT` protects assets referenced by snapshots.

Do not alter SQL statements or transaction order.

- [ ] **Step 2: Add MinIO bootstrap rationale comments**

Use `#` comments to explain:

- root credentials belong only to the MinIO server and one-shot bootstrap, never the backend;
- both buckets are explicitly private;
- backend credentials receive only bucket location/list and object get/put/delete for configured originals/generated buckets;
- `--ignore-existing`, repeatable user update, and policy recreation make initialization safe after ordinary restarts/config updates.

Do not claim commands are concurrency-safe beyond the one-shot Compose initializer.

- [ ] **Step 3: Run full verification and commit Task 4**

Run:

```bash
PATH=/home/charlie/.nvm/versions/node/v24.18.0/bin:$PATH make verify
sh infrastructure/minio/configuration-test.sh
rg -n "A-to-B-to-A|unambiguous|nullable|ON DELETE RESTRICT|root credentials|private|ordinary restart" backend/src/main/resources/db/migration infrastructure/minio/create-bucket.sh
! rg -n "TODO|FIXME|TBD|XXX" backend/src/main/resources/db/migration infrastructure/minio/create-bucket.sh
git diff --check
```

Expected: backend 101 tests, frontend 51 tests plus lint/typecheck/build, worker 6 tests, infrastructure/Compose checks, and comment scans pass with Node 24.

Commit:

```bash
git add backend/src/main/resources/db/migration/V3__description_revision_ownership.sql \
  backend/src/main/resources/db/migration/V5__retain_caption_job_results.sql \
  backend/src/main/resources/db/migration/V8__publication_asset_associations.sql \
  infrastructure/minio/create-bucket.sh
git commit -m "docs: explain migration and bootstrap safety"
```
