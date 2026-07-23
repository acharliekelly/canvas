# CANVAS Local MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a locally runnable CANVAS demo in which one configured administrator uploads artwork, creates or generates multiple descriptions, approves and publishes them, and receives a public page with cached placeholder audio and a downloadable QR code.

**Architecture:** Deliver the MVP as seven vertical milestones across a React client, Spring Boot modular monolith, PostgreSQL, MinIO, and a deterministic FastAPI caption worker. Spring owns all workflow state and publication rules; external behavior is accessed through replaceable caption, audio, storage, and QR ports.

**Tech Stack:** Java 21, Spring Boot 4.1.x, Maven Wrapper, PostgreSQL, Flyway, AWS SDK for Java S3 client, React 19.2.x, TypeScript, Vite, Node.js 24 LTS, npm, Python 3.13, FastAPI, Docker Compose, MinIO, Vitest, Testing Library, Playwright, pytest, Testcontainers, axe-core, and ZXing.

## Global Constraints

- The demo must require no GPU, cloud account, paid service, or real model integration.
- `frontend/` owns browser interfaces; `backend/` owns workflow and persistence; `caption-worker/` owns only the caption contract; `infrastructure/` owns orchestration.
- The backend is the sole authority for approval and publication.
- An artwork owns zero or more ordered, free-form-labeled descriptions.
- Both manual and generated descriptions use revisioned `DRAFT` and `APPROVED` states.
- Publication requires at least one approved description.
- Public responses contain only explicitly published, approved revisions.
- Audio and QR assets are generated from approved publication inputs and reused by content key.
- Admin authentication uses one environment-configured account, HTTP-only session cookies, and CSRF protection.
- Uploads are validated by size, media type, and image decodeability.
- Default tests must not require a GPU.
- Accessibility is a release criterion and requires automated plus manual checks.
- Use supported stable dependencies and commit generated lockfiles and wrappers.
- Do not put credentials, private keys, or real artwork in the repository.

## Planned file structure

```text
canvas/
├── .env.example                         documented non-secret configuration
├── compose.yaml                         local full-stack orchestration
├── Makefile                             stable developer commands
├── frontend/
│   ├── src/api/                         typed HTTP client and API models
│   ├── src/auth/                        session provider and sign-in form
│   ├── src/artworks/                    admin list, upload, and editor
│   ├── src/publication/                 public artwork page
│   ├── src/test/                        shared browser-test setup
│   └── e2e/                             core Playwright journeys
├── backend/
│   ├── src/main/java/me.acharliekelly.canvas/identity/
│   ├── src/main/java/me.acharliekelly.canvas/artwork/
│   ├── src/main/java/me.acharliekelly.canvas/description/
│   ├── src/main/java/me.acharliekelly.canvas/caption/
│   ├── src/main/java/me.acharliekelly.canvas/publication/
│   ├── src/main/java/me.acharliekelly.canvas/storage/
│   ├── src/main/java/me.acharliekelly.canvas/shared/
│   ├── src/main/resources/db/migration/
│   └── src/test/java/me.acharliekelly.canvas/
├── caption-worker/
│   ├── src/canvas_caption_worker/       deterministic HTTP worker
│   └── tests/                           contract tests
├── infrastructure/minio/                bucket initialization
└── docs/manual-testing.md                keyboard and screen-reader checks
```

Every Java package named above must include a Spring Modulith `package-info.java` with `@ApplicationModule`; only each module's `api` package is exported. Domain entities remain package-private. REST DTOs never expose JPA entities or storage keys.

---

### Task 1: Runnable full-stack foundation and health slice

**Deliverable:** `docker compose up --build` starts PostgreSQL, MinIO, the backend, caption worker, and frontend; the browser renders CANVAS and reports backend readiness.

**Files:**
- Create: `.editorconfig`, `.gitignore`, `.env.example`, `compose.yaml`, `Makefile`
- Create: `backend/pom.xml`, `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/*`
- Create: `backend/src/main/java/me.acharliekelly.canvas/CanvasApplication.java`
- Create: `backend/src/main/java/me.acharliekelly.canvas/shared/api/HealthController.java`
- Create: `backend/src/main/resources/application.yml`, `backend/src/test/resources/application-test.yml`
- Create: `backend/src/test/java/me.acharliekelly.canvas/shared/api/HealthControllerTest.java`
- Create: `backend/Dockerfile`
- Create: `frontend/package.json`, `frontend/package-lock.json`, `frontend/tsconfig*.json`, `frontend/vite.config.ts`
- Create: `frontend/src/main.tsx`, `frontend/src/App.tsx`, `frontend/src/App.test.tsx`, `frontend/src/styles.css`, `frontend/src/test/setup.ts`
- Create: `frontend/Dockerfile`
- Create: `caption-worker/pyproject.toml`, `caption-worker/src/canvas_caption_worker/main.py`, `caption-worker/tests/test_health.py`, `caption-worker/Dockerfile`
- Create: `infrastructure/minio/create-bucket.sh`

**Interfaces:**
- Produces: `GET /api/health -> {"status":"ready"}`
- Produces: `GET /health -> {"status":"ready"}` on the caption worker
- Produces: Make targets `up`, `down`, `test`, `test-backend`, `test-frontend`, `test-worker`

- [ ] **Step 1: Generate supported project wrappers and lockfiles**

Use Spring Initializr for Maven, Java 21, Spring Boot 4.1.x with Web, Validation, Security, Data JPA, Actuator, Flyway, PostgreSQL, and Testcontainers. Generate the frontend with Vite's React TypeScript template under Node 24 LTS. Initialize the worker with Python 3.13, FastAPI, Uvicorn, pytest, and HTTPX. Commit Maven wrapper and npm lockfiles; pin direct Python dependencies in `pyproject.toml`.

Run:

```bash
java -version
node --version
python3 --version
```

Expected: Java 21.x, Node 24.x, and Python 3.13.x. If the host differs, continue through the pinned container builds.

- [ ] **Step 2: Write failing health tests**

```java
@WebMvcTest(HealthController.class)
class HealthControllerTest {
  @Autowired MockMvc mvc;

  @Test void reportsReady() throws Exception {
    mvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ready"));
  }
}
```

```tsx
it("shows the application and backend status", async () => {
  render(<App />);
  expect(screen.getByRole("heading", { name: "CANVAS" })).toBeVisible();
  expect(await screen.findByText("System ready")).toBeVisible();
});
```

```python
def test_health(client):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ready"}
```

- [ ] **Step 3: Run tests to verify the slice is absent**

Run:

```bash
cd backend && ./mvnw test -Dtest=HealthControllerTest
cd frontend && npm test -- --run src/App.test.tsx
cd caption-worker && python -m pytest tests/test_health.py -q
```

Expected: each focused test fails because its application entry point or health behavior is not implemented.

- [ ] **Step 4: Implement the minimal health slice and Compose topology**

The backend controller returns `Map.of("status", "ready")`. The worker exposes the same payload. `App` fetches `/api/health`, renders a semantic `<main>`, `<h1>CANVAS</h1>`, and a `role="status"` readiness message. Vite proxies `/api` to the backend in development.

Configure Compose health checks and `depends_on` conditions. Use named volumes `canvas-postgres-data` and `canvas-minio-data`. The MinIO initializer creates private `canvas-originals` and `canvas-generated` buckets. `.env.example` defines local-only sample values for database, MinIO, admin username, BCrypt password hash, public base URL, upload size, and caption-worker URL.

- [ ] **Step 5: Verify the milestone**

Run:

```bash
make test
docker compose config --quiet
docker compose up --build --wait
curl --fail http://localhost:8080/api/health
curl --fail http://localhost:8000/health
curl --fail http://localhost:5173/
docker compose down
```

Expected: tests pass, Compose becomes healthy, both health endpoints return `{"status":"ready"}`, and the frontend HTML is returned.

- [ ] **Step 6: Commit**

```bash
git add .editorconfig .gitignore .env.example compose.yaml Makefile backend frontend caption-worker infrastructure
git commit -m "build: establish runnable CANVAS stack"
```

---

### Task 2: Authenticated artwork upload vertical slice

**Deliverable:** The configured administrator signs in, uploads a validated image with metadata, and sees it in a persistent artwork list after restart.

**Files:**
- Create: `backend/src/main/java/me.acharliekelly.canvas/identity/SecurityConfiguration.java`
- Create: `backend/src/main/java/me.acharliekelly.canvas/identity/api/SessionController.java`, `SessionResponse.java`
- Create: `backend/src/main/java/me.acharliekelly.canvas/artwork/Artwork.java`, `ArtworkRepository.java`, `ArtworkService.java`
- Create: `backend/src/main/java/me.acharliekelly.canvas/artwork/api/ArtworkController.java`, `ArtworkSummary.java`, `ArtworkDetail.java`
- Create: `backend/src/main/java/me.acharliekelly.canvas/storage/ObjectStorage.java`, `S3ObjectStorage.java`, `StorageConfiguration.java`
- Create: `backend/src/main/java/me.acharliekelly.canvas/shared/api/ApiExceptionHandler.java`
- Create: `backend/src/main/resources/db/migration/V1__artworks.sql`
- Create: `backend/src/test/java/me.acharliekelly.canvas/identity/SessionApiTest.java`
- Create: `backend/src/test/java/me.acharliekelly.canvas/artwork/ArtworkApiTest.java`, `ArtworkUploadIntegrationTest.java`
- Create: `frontend/src/api/client.ts`, `frontend/src/api/types.ts`
- Create: `frontend/src/auth/SignInPage.tsx`, `SignInPage.test.tsx`, `SessionProvider.tsx`
- Create: `frontend/src/artworks/ArtworkListPage.tsx`, `ArtworkListPage.test.tsx`, `ArtworkUploadForm.tsx`, `ArtworkUploadForm.test.tsx`

**Interfaces:**
- Produces: `GET /api/session -> SessionResponse(authenticated: boolean, username: string | null, csrfToken: string)`
- Produces: `POST /api/login` as form login and `POST /api/logout`
- Produces: `POST /api/artworks` multipart fields `image`, `title`, `credit`, `context?`
- Produces: `GET /api/artworks -> ArtworkSummary[]`
- Produces: `GET /api/artworks/{artworkId} -> ArtworkDetail`
- Produces: `ObjectStorage.put(InputStream, long, String): StoredObject` and `delete(String): void`

- [ ] **Step 1: Write failing backend authentication and upload tests**

Test that unauthenticated `/api/artworks` requests return 401; correct configured credentials establish a session; CSRF is required for upload; valid PNG and JPEG uploads return 201; invalid MIME, oversized, and undecodable images return RFC 9457 problem details; a storage failure leaves no artwork row; and list/detail responses never include the object key.

```java
@Test void publicationDataIsNotExposedFromAnUpload() throws Exception {
  mvc.perform(multipart("/api/artworks")
      .file(validPng()).param("title", "Blue Study").param("credit", "A. Artist")
      .with(user("admin")).with(csrf()))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.title").value("Blue Study"))
      .andExpect(jsonPath("$.imageObjectKey").doesNotExist());
}
```

- [ ] **Step 2: Run focused backend tests and confirm failure**

Run: `cd backend && ./mvnw test -Dtest=SessionApiTest,ArtworkApiTest,ArtworkUploadIntegrationTest`

Expected: FAIL because authentication, schema, storage port, and artwork endpoints do not exist.

- [ ] **Step 3: Implement security, storage, migration, and artwork API**

Use environment properties `CANVAS_ADMIN_USERNAME` and `CANVAS_ADMIN_PASSWORD_HASH` to construct one in-memory Spring Security user. Return JSON 401/403 responses for API requests. Expose the CSRF token through `SessionResponse` so `apiFetch` sends it in `X-CSRF-TOKEN` for mutations.

`V1__artworks.sql` creates UUID primary keys, title, credit, nullable context, media type, byte size, object key, lifecycle status `UPLOADED`, nullable public slug, `version`, and audit timestamps. Validate the declared type, configured byte limit, and ImageIO decode before calling `ObjectStorage.put`. If persistence fails after storage succeeds, compensate with `delete` and log both failures without returning the object key.

- [ ] **Step 4: Write failing frontend sign-in, upload, and list tests**

Test semantic labels, credential errors, keyboard submission, image requirement, server validation messages, upload progress status, success focus placement, and persistent list rendering. Stub only the typed `apiFetch` boundary.

```tsx
it("announces a completed upload and adds the artwork", async () => {
  render(<ArtworkListPage />);
  await user.upload(screen.getByLabelText("Artwork image"), validPng);
  await user.type(screen.getByLabelText("Title"), "Blue Study");
  await user.type(screen.getByLabelText("Artist or display credit"), "A. Artist");
  await user.click(screen.getByRole("button", { name: "Upload artwork" }));
  expect(await screen.findByRole("status")).toHaveTextContent("Blue Study uploaded");
  expect(screen.getByRole("link", { name: /Blue Study/ })).toBeVisible();
});
```

- [ ] **Step 5: Implement the authenticated frontend slice**

`SessionProvider` loads `/api/session`; routes requiring authentication render `SignInPage`; `apiFetch` includes credentials and CSRF. Use native semantic form controls and an error summary linked to invalid fields. The artwork list shows title, credit, `UPLOADED`, and an edit link.

- [ ] **Step 6: Verify the milestone**

Run:

```bash
cd backend && ./mvnw test
cd frontend && npm test -- --run
docker compose up --build --wait
```

Manually sign in, upload an image, restart with `docker compose restart`, and confirm the artwork remains. Confirm keyboard-only sign-in and upload, visible focus, announced errors, and no object key in browser network responses.

- [ ] **Step 7: Commit**

```bash
git add backend frontend
git commit -m "feat: add authenticated artwork upload"
```

---

### Task 3: Multiple manual descriptions, revisions, ordering, and approval

**Deliverable:** An administrator adds zero or more labeled descriptions, orders them, edits drafts, approves revisions, and sees immutable approval history.

**Files:**
- Create: `backend/src/main/java/me.acharliekelly.canvas/description/Description.java`, `DescriptionRevision.java`, `DescriptionSource.java`, `RevisionState.java`
- Create: `backend/src/main/java/me.acharliekelly.canvas/description/DescriptionRepository.java`, `DescriptionService.java`
- Create: `backend/src/main/java/me.acharliekelly.canvas/description/api/DescriptionController.java`, `DescriptionResponse.java`, `CreateDescriptionRequest.java`, `UpdateDraftRequest.java`, `ReorderDescriptionsRequest.java`
- Create: `backend/src/main/resources/db/migration/V2__descriptions.sql`
- Create: `backend/src/test/java/me.acharliekelly.canvas/description/DescriptionServiceTest.java`, `DescriptionApiTest.java`
- Create: `frontend/src/artworks/ArtworkEditorPage.tsx`, `ArtworkEditorPage.test.tsx`
- Create: `frontend/src/artworks/DescriptionEditor.tsx`, `DescriptionEditor.test.tsx`, `ApproveDescriptionDialog.tsx`

**Interfaces:**
- Produces: `POST /api/artworks/{artworkId}/descriptions` with `{label, text}`
- Produces: `PUT /api/artworks/{artworkId}/descriptions/{descriptionId}/draft` with `{label, text, version}`
- Produces: `PUT /api/artworks/{artworkId}/description-order` with `{descriptionIds, version}`
- Produces: `POST /api/artworks/{artworkId}/descriptions/{descriptionId}/approve` with `{version}`
- Produces: `DescriptionService.createGeneratedDraft(UUID, String, String): DescriptionResponse` for Task 4

- [ ] **Step 1: Write failing domain and API tests**

Cover zero descriptions, two free-form labels (`Objective`, `Subjective`), stable ordering, required nonblank labels/text, ownership checks, optimistic-lock conflicts, explicit approval, approver/timestamp audit, and editing after approval creating a new `DRAFT` revision while retaining the approved revision.

```java
@Test void editingApprovedDescriptionCreatesDraftRevision() {
  var approved = service.approve(createManual("Objective", "A blue square."), ADMIN_ID, 0);
  var edited = service.updateDraft(approved.descriptionId(), "Objective", "A cobalt square.", approved.version());
  assertThat(edited.currentRevision().state()).isEqualTo(DRAFT);
  assertThat(repository.findRevision(approved.approvedRevisionId()).text()).isEqualTo("A blue square.");
}
```

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `cd backend && ./mvnw test -Dtest=DescriptionServiceTest,DescriptionApiTest`

Expected: FAIL because the description schema and service do not exist.

- [ ] **Step 3: Implement revisioned descriptions**

`V2__descriptions.sql` creates `descriptions` for stable identity/source/order and `description_revisions` for text, label, state, parent revision, approver, approval time, and audit data. Add unique constraints for `(artwork_id, display_order)` and one current draft pointer per description. Keep approved revisions immutable in service methods and transactions.

Return 409 problem details with code `stale_version` on optimistic-lock failure. Reordering validates that the submitted IDs exactly match the artwork's current descriptions.

- [ ] **Step 4: Write failing editor interaction tests**

Cover adding multiple cards, free-form labels, saving drafts, moving descriptions up/down by buttons, disabled boundary controls that preserve values, explicit approval confirmation, approval status text, and editing an approved item producing a new draft notice. Include axe assertions for the editor's initial and validation-error states.

- [ ] **Step 5: Implement the manual-description editor**

Render each description in a labeled `<section>` with status text. Use `Move up` and `Move down` buttons rather than pointer-only drag and drop. Approval opens a native accessible dialog or an equivalent focus-managed modal, names the description, states that later edits create a new draft, and requires `Approve description` confirmation.

- [ ] **Step 6: Verify the milestone**

Run:

```bash
cd backend && ./mvnw test
cd frontend && npm test -- --run
```

Manually add Objective and Subjective descriptions, reorder them with keyboard only, approve both, edit Objective, and confirm the earlier approved text remains in the API history while the editor shows a new draft.

- [ ] **Step 7: Commit**

```bash
git add backend frontend
git commit -m "feat: add revisioned artwork descriptions"
```

---

### Task 4: Deterministic caption-generation vertical slice

**Deliverable:** Caption generation is optional; requesting it creates a pollable job and ultimately a new editable `GENERATED` description without affecting manual descriptions.

**Files:**
- Modify: `caption-worker/src/canvas_caption_worker/main.py`
- Create: `caption-worker/src/canvas_caption_worker/contracts.py`, `caption-worker/tests/test_caption.py`
- Create: `backend/src/main/java/me.acharliekelly.canvas/caption/CaptionClient.java`, `HttpCaptionClient.java`, `CaptionJob.java`, `CaptionJobRepository.java`, `CaptionJobService.java`, `CaptionJobRunner.java`
- Create: `backend/src/main/java/me.acharliekelly.canvas/caption/api/CaptionJobController.java`, `CaptionJobResponse.java`
- Create: `backend/src/main/resources/db/migration/V4__caption_jobs.sql`, `V5__retain_caption_job_results.sql`
- Create: `backend/src/test/java/me.acharliekelly.canvas/caption/CaptionJobServiceTest.java`, `CaptionJobIntegrationTest.java`
- Create: `frontend/src/artworks/CaptionRequestPanel.tsx`, `CaptionRequestPanel.test.tsx`

**Interfaces:**
- Worker consumes: `CaptionRequest(imageUrl: string, title: string, credit: string, context: string | null)`
- Worker produces: `CaptionResponse(label: string, text: string, engine: "deterministic-placeholder", engineVersion: "1")`
- Produces: `POST /api/artworks/{artworkId}/caption-jobs -> 202 CaptionJobResponse`
- Produces: `GET /api/artworks/{artworkId}/caption-jobs/{jobId} -> CaptionJobResponse`

- [ ] **Step 1: Write failing worker contract tests**

```python
def test_caption_is_deterministic(client):
    payload = {"imageUrl": "http://backend/internal/a", "title": "Blue Study", "credit": "A. Artist", "context": None}
    first = client.post("/captions", json=payload)
    second = client.post("/captions", json=payload)
    assert first.status_code == 200
    assert first.json() == second.json()
    assert first.json()["engine"] == "deterministic-placeholder"
```

Also reject missing title, unsupported URL schemes, and extra fields.

- [ ] **Step 2: Write failing backend job tests**

Cover `PENDING -> RUNNING -> SUCCEEDED`, failed worker calls becoming `FAILED` with a safe message, retry creating or resuming one active job, successful jobs creating exactly one generated draft, and manual descriptions remaining untouched.

- [ ] **Step 3: Run focused tests and confirm failure**

Run:

```bash
cd caption-worker && python -m pytest tests/test_caption.py -q
cd backend && ./mvnw test -Dtest=CaptionJobServiceTest,CaptionJobIntegrationTest
```

Expected: FAIL because the caption endpoint and job orchestration are absent.

- [ ] **Step 4: Implement the worker and durable job runner**

The placeholder response uses artwork metadata in a plainly identified demo sentence and never claims to inspect image content. `V4__caption_jobs.sql` stores job state, attempt count, safe error message, resulting description ID, timestamps, and optimistic version. Forward migration `V5__retain_caption_job_results.sql` replaces V4's contradictory result-description `ON DELETE SET NULL` behavior with `ON DELETE RESTRICT` so successful-job audit references remain valid; do not rewrite V4 because it may already be applied.

Use a bounded Spring task executor. The transaction that creates the job commits before execution begins. The runner claims a pending job, calls `CaptionClient`, then invokes `DescriptionService.createGeneratedDraft`. On failure it records `FAILED`; retry returns the active job or creates a new attempt from a failed terminal job. Add structured logs with job and artwork IDs.

- [ ] **Step 5: Write failing frontend polling tests**

Use fake timers to prove that the panel announces pending, polls only while non-terminal, displays a retry action after failure, adds the generated description on success, and stops polling when unmounted.

- [ ] **Step 6: Implement the caption request panel**

Label the action `Generate placeholder draft`. Explain that it is deterministic demo text, not image analysis. Poll with capped backoff from one to five seconds. Put updates in `role="status"` and return focus to the generated description heading on success.

- [ ] **Step 7: Verify and commit**

Run all backend, frontend, and worker tests. With Compose running, stop the worker, request generation, confirm a retryable failure, restart it, retry, and confirm a new `GENERATED` draft appears without altering manual descriptions.

```bash
git add backend frontend caption-worker
git commit -m "feat: add placeholder caption workflow"
```

---

### Task 5: Revision-safe publication and public artwork page

**Deliverable:** Publishing requires an approved description and creates an immutable public snapshot that exposes only ordered approved revisions.

**Files:**
- Create: `backend/src/main/java/me.acharliekelly.canvas/publication/Publication.java`, `PublishedDescription.java`, `PublicationRepository.java`, `PublicationService.java`
- Create: `backend/src/main/java/me.acharliekelly.canvas/publication/api/PublicationController.java`, `PublicArtworkController.java`, `PublicArtworkResponse.java`
- Create: `backend/src/main/resources/db/migration/V6__publications.sql`
- Create: `backend/src/test/java/me.acharliekelly.canvas/publication/PublicationServiceTest.java`, `PublicArtworkApiTest.java`
- Create: `frontend/src/artworks/PublishPanel.tsx`, `PublishPanel.test.tsx`
- Create: `frontend/src/publication/PublicArtworkPage.tsx`, `PublicArtworkPage.test.tsx`

**Interfaces:**
- Produces: `POST /api/artworks/{artworkId}/publication` with `{version}`
- Produces: `GET /public/artworks/{slug} -> PublicArtworkResponse`
- Produces: `PublicationService.publish(UUID artworkId, long version, UUID administratorId): PublicationResult`
- Consumes: approved description revisions from Task 3

- [ ] **Step 1: Write failing publication tests**

Cover rejection with zero approved descriptions, exclusion of drafts, preservation of approved label/order/text, slug stability, idempotent repeated publication, republishing after a new approval creating a new snapshot, public 404 before publication, and no internal IDs, storage keys, drafts, or audit identities in public JSON.

```java
@Test void requiresAtLeastOneApprovedDescription() {
  assertThatThrownBy(() -> service.publish(artworkWithDraftOnly(), 0, ADMIN_ID))
      .isInstanceOf(PublicationNotAllowed.class)
      .hasMessageContaining("Approve at least one description");
}
```

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `cd backend && ./mvnw test -Dtest=PublicationServiceTest,PublicArtworkApiTest`

Expected: FAIL because publication persistence and APIs do not exist.

- [ ] **Step 3: Implement snapshot publication**

`V6__publications.sql` creates versioned publication rows plus copied published-description rows referencing their approved revision IDs. Generate a stable collision-resistant lowercase slug once per artwork. In one transaction, lock the artwork, select current approved revisions in display order, enforce the invariant, create the snapshot, and mark it current. A content hash makes identical repeated requests idempotent.

Serve the artwork image through a backend public endpoint that resolves the stored object internally; never reveal MinIO credentials or keys.

- [ ] **Step 4: Write failing admin and public page tests**

Test disabled publication with an explanatory message when nothing is approved, confirmation before publication, actionable server errors, success link, labeled descriptions in order, absence of drafts, meaningful image alternative text, document title, landmark structure, and an axe scan.

- [ ] **Step 5: Implement publication UI and public page**

The admin panel explicitly lists which approved descriptions will publish. The public route `/artworks/:slug` renders one `<main>`, one `<h1>`, artwork credit, the image, and labeled description sections in stored order. Use the first approved Objective-like description as image alt text only when concise enough; otherwise use `Artwork: {title}. Full descriptions follow.` to avoid duplicating long prose.

- [ ] **Step 6: Verify and commit**

Run backend and frontend suites. Manually confirm publication is blocked for drafts, publish two approved descriptions, create a new draft from one, and verify the public page continues showing the approved snapshot until republished.

```bash
git add backend frontend
git commit -m "feat: publish approved artwork descriptions"
```

---

### Task 6: Cached placeholder audio and QR assets

**Deliverable:** Publication creates or reuses valid placeholder audio per approved description and a downloadable QR code for the stable public URL.

**Files:**
- Create: `backend/src/main/java/me.acharliekelly.canvas/publication/asset/GeneratedAsset.java`, `GeneratedAssetRepository.java`, `AssetKind.java`
- Create: `backend/src/main/java/me.acharliekelly.canvas/publication/asset/AudioGenerator.java`, `PlaceholderAudioGenerator.java`, `QrCodeGenerator.java`, `ZxingQrCodeGenerator.java`, `AssetService.java`
- Create: `backend/src/main/java/me.acharliekelly.canvas/publication/api/PublicAssetController.java`
- Create: `backend/src/main/resources/db/migration/V7__generated_assets.sql`
- Create: `backend/src/main/resources/audio/placeholder.wav`
- Create: `backend/src/test/java/me.acharliekelly.canvas/publication/asset/AssetServiceTest.java`, `PublicAssetApiTest.java`
- Modify: `backend/src/main/java/me.acharliekelly.canvas/publication/PublicationService.java`
- Modify: `frontend/src/publication/PublicArtworkPage.tsx`, `PublicArtworkPage.test.tsx`
- Modify: `frontend/src/artworks/PublishPanel.tsx`, `PublishPanel.test.tsx`

**Interfaces:**
- Produces: `AudioGenerator.generate(ApprovedDescriptionInput): GeneratedBinary`
- Produces: `QrCodeGenerator.generate(URI publicUri): GeneratedBinary`
- Produces: `GET /public/artworks/{slug}/descriptions/{publishedDescriptionId}/audio`
- Produces: `GET /public/artworks/{slug}/qr` with `Content-Disposition: attachment`
- Consumes: `ObjectStorage` from Task 2 and publication snapshot from Task 5

- [ ] **Step 1: Write failing asset tests**

Cover SHA-256 content keys, one audio asset per approved revision input, one QR per stable public URL, reuse across identical publication retries, replacement after changed approved text, valid WAV headers, valid PNG dimensions, public cache headers, inaccessible unpublished assets, and cleanup after storage failure.

```java
@Test void reusesAudioForTheSameApprovedRevision() {
  var first = assets.audioFor(approvedRevision);
  var second = assets.audioFor(approvedRevision);
  assertThat(second.id()).isEqualTo(first.id());
  verify(audioGenerator, times(1)).generate(any());
}
```

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `cd backend && ./mvnw test -Dtest=AssetServiceTest,PublicAssetApiTest`

Expected: FAIL because generated-asset persistence and ports do not exist.

- [ ] **Step 3: Implement content-addressed asset generation**

`V7__generated_assets.sql` stores kind, SHA-256 input key, media type, byte size, object key, source revision or publication, and audit timestamps with a uniqueness constraint on `(kind, input_key)`.

`PlaceholderAudioGenerator` returns the checked-in short silent WAV and identifies it as placeholder output; the visible transcript always remains the approved description text. `ZxingQrCodeGenerator` encodes `${CANVAS_PUBLIC_BASE_URL}/artworks/{slug}` as a high-contrast PNG with a quiet zone. `AssetService` checks the content key before generation, stores through `ObjectStorage`, and compensates on transaction failure.

Publication first ensures all snapshot assets exist; any failure leaves the prior publication current and the new attempt retryable. Only after asset success does the new snapshot become current.

- [ ] **Step 4: Extend frontend tests and implementation**

For every published description render its visible transcript and associated `<audio controls preload="none">` with an accessible label. Add `Download QR code for {title}` to the admin success panel. Do not autoplay audio or hide text when audio is present.

- [ ] **Step 5: Verify and commit**

Run the backend and frontend suites. Publish, download the QR PNG, scan it with a local QR decoder or phone, play the WAV, republish unchanged content, and confirm database/storage asset counts do not increase.

```bash
git add backend frontend
git commit -m "feat: add cached publication audio and QR assets"
```

---

### Task 7: End-to-end acceptance, accessibility, restart proof, and operator documentation

**Deliverable:** Both editorial paths pass automated and manual acceptance checks from a clean checkout, including persistence across restart.

**Files:**
- Create: `frontend/playwright.config.ts`
- Create: `frontend/e2e/manual-description-publication.spec.ts`
- Create: `frontend/e2e/generated-description-publication.spec.ts`
- Create: `frontend/e2e/accessibility.spec.ts`
- Create: `frontend/e2e/fixtures/sample-artwork.png`
- Create: `backend/src/test/java/me.acharliekelly.canvas/architecture/ModuleBoundariesTest.java`
- Create: `docs/manual-testing.md`
- Modify: `README.md`, `Makefile`, `compose.yaml`, `.env.example`

**Interfaces:**
- Produces: `make verify` for all non-GPU automated checks
- Produces: `make e2e` for Playwright against Compose
- Produces: a clean-checkout setup and reset procedure

- [ ] **Step 1: Write failing end-to-end journeys**

The manual journey signs in, uploads the fixture, adds Objective and Subjective descriptions, reorders and approves them, publishes, opens the public page, verifies ordered visible text and audio, and downloads a QR PNG.

The generated journey uploads an artwork with no descriptions, verifies publication is blocked, requests a placeholder draft, waits for completion, edits and approves it, publishes, and verifies the public page.

The accessibility journey runs axe on sign-in, artwork list, upload errors, editor with multiple descriptions, approval dialog, publication confirmation, and public page. Configure Playwright downloads so the QR file's PNG signature is asserted.

- [ ] **Step 2: Add architecture and migration checks**

Use Spring Modulith's `ApplicationModules.of(CanvasApplication.class).verify()` to reject illegal module dependencies. Add a migration-from-empty integration test and a schema validation startup test. Ensure tests fail before package boundaries and the final scripts are wired.

- [ ] **Step 3: Implement stable verification commands**

`make verify` runs, in order:

```bash
cd backend && ./mvnw verify
cd frontend && npm ci && npm run lint && npm run typecheck && npm test -- --run && npm run build
cd caption-worker && python -m pytest -q
docker compose config --quiet
```

`make e2e` builds and starts Compose, waits on health checks, runs Playwright, and always stops the stack without deleting named volumes. Provide a separate explicit `make reset-local-data` command that requires `CONFIRM=1` before deleting the two named project volumes.

- [ ] **Step 4: Write exact operator and manual accessibility guidance**

Update `README.md` with prerequisites, `.env` creation, BCrypt hash generation, startup, service URLs, health checks, test commands, logs, restart, and opt-in reset instructions. Correct its foundation-spec link to this design if the old target does not exist.

`docs/manual-testing.md` must provide expected results for:

- keyboard-only sign-in, upload, add/reorder/edit/approve, publish, audio, and QR download;
- visible focus and logical focus movement after errors and dialogs;
- NVDA or VoiceOver reading of headings, labels, status announcements, errors, description order, audio labels, and transcripts;
- 200% zoom and narrow viewport without loss of content or operation;
- contrast and non-color status cues;
- verification that generated drafts are identified and unpublished drafts never appear publicly.

- [ ] **Step 5: Run full automated verification from a clean application state**

Run:

```bash
make verify
CONFIRM=1 make reset-local-data
make e2e
docker compose restart
make e2e-persistence-check
```

Expected: all unit, integration, architecture, frontend, worker, build, Compose, axe, and Playwright checks pass; the persistence check finds the previously published artwork and its cached assets after restart.

- [ ] **Step 6: Perform and record manual confirmation**

Follow every check in `docs/manual-testing.md` using keyboard only and at least one screen reader. Record date, browser, screen reader/version, pass/fail, and any issue links in the document's results table. Do not mark the MVP complete if a release-criterion check fails.

- [ ] **Step 7: Commit**

```bash
git add README.md .env.example compose.yaml Makefile backend frontend docs/manual-testing.md
git commit -m "test: verify local MVP workflows"
```

---

## Final acceptance gate

Before claiming completion:

1. Run `git status --short` and account for every file.
2. Run `make verify` and retain the complete successful output.
3. Reset local application data and run `make e2e`.
4. Restart the stack and run the persistence check.
5. Complete the manual keyboard and screen-reader checklist.
6. Confirm no test required GPU or external network access.
7. Confirm `.env`, uploaded files, credentials, and generated local data are ignored.
8. Confirm the public API contains no drafts, administrator identity, storage keys, or internal errors.
9. Confirm unchanged republication reuses audio and QR assets.
10. Confirm README commands work from a new checkout.

## Suggested integration commit sequence

```text
build: establish runnable CANVAS stack
feat: add authenticated artwork upload
feat: add revisioned artwork descriptions
feat: add placeholder caption workflow
feat: publish approved artwork descriptions
feat: add cached publication audio and QR assets
test: verify local MVP workflows
```
