# Task 1 Report: Runnable full-stack foundation and health slice

## Status

Implemented and verified on `feature/local-mvp` from baseline
`7e6fab3c7a2553fe84dfb3a9aa75578db29fdfdd`.

## Implementation

- Added a Spring Boot 4.1.0 Java 21 backend with Maven Wrapper, `/api/health`,
  and a narrowly scoped security rule allowing the health endpoint.
- Added a FastAPI Python 3.13 caption worker with `/health`.
- Added a React/TypeScript Vite frontend. It uses semantic `<main>` and
  `<h1>CANVAS</h1>`, fetches `/api/health`, and announces backend readiness in
  `role="status"`.
- Added Compose services for PostgreSQL, MinIO, its bucket initializer, backend,
  caption worker, and frontend. PostgreSQL and MinIO use the required named
  volumes. The initializer creates private `canvas-originals` and
  `canvas-generated` buckets.
- Added Dockerfiles and test stages pinned to Java 21, Node 24, and Python 3.13;
  Make targets run each test suite in those pinned runtimes because this host is
  Java 25, Node 23, and Python 3.12.
- Added local-only `.env.example`, editor settings, ignore rules, Compose health
  checks and service ordering, and `up`, `down`, `test`, `test-backend`,
  `test-frontend`, and `test-worker` Make targets.

## TDD evidence

### RED

- `MAVEN_USER_HOME=/tmp/canvas-m2 ./mvnw test -Dtest=HealthControllerTest -q`
  failed at test compilation because `HealthController` did not exist.
- A Node 24 Docker test image failed because `src/App.tsx` did not exist.
- A Python 3.13 Docker test image failed during collection because
  `canvas_caption_worker.main` did not exist.

### GREEN

- Focused backend health test passed after adding the controller and health-only
  security rule.
- Node 24 frontend test: `1 passed`.
- Python 3.13 worker test: `1 passed`.

## Final verification

- `make test` — passed: backend `1` test, frontend `1` test, worker `1` test.
- `docker compose config --quiet` — passed.
- `docker compose up --build --wait` — passed with PostgreSQL, MinIO,
  MinIO initializer, backend, caption worker, and frontend healthy (initializer
  exited successfully as expected).
- `curl --fail http://localhost:8080/api/health` — returned `{"status":"ready"}`.
- `curl --fail http://localhost:8000/health` — returned `{"status":"ready"}`.
- `curl --fail http://localhost:5173/` — returned the Vite-served CANVAS HTML.
- `docker compose down` — passed; named volumes were preserved.
- `git diff --check` — passed.

## Files changed

- Root: `.editorconfig`, `.gitignore`, `.env.example`, `compose.yaml`,
  `Makefile`.
- Backend: Spring Initializr Maven project, wrapper, Dockerfile, health
  controller/application/security configuration, and MVC test.
- Frontend: Vite React TypeScript configuration, lockfile, Dockerfile, app,
  readiness test, styles, and test setup.
- Worker: pinned Python project metadata, FastAPI app, test fixture/health test,
  and Dockerfile.
- Infrastructure: MinIO bucket initialization script.

## Self-review

- Confirmed both required health payloads are exactly `{"status":"ready"}`.
- Confirmed the worker is published on host port `8000`; this was corrected after
  the first endpoint check exposed the missing mapping.
- Confirmed the worker's final image is the Uvicorn application stage rather
  than its test stage; this was corrected after the first Compose attempt exited
  successfully after running pytest.
- No workflow, model, GPU, persisted schema, or publication behavior was added;
  those remain intentionally outside Task 1.

## Manual confirmation

1. Copy `.env.example` to `.env` if local values need changing.
2. Run `make up`.
3. Open `http://localhost:5173/` and confirm CANVAS is visible and the status
   changes to `System ready`.
4. Run `make down` when finished.

## Concerns

The host does not provide the requested Java 21, Node 24, or Python 3.13
versions. All executable tests and Compose builds therefore used their pinned
container runtimes; this matches the task brief's host-version fallback.

## Suggested commit

`build: establish runnable CANVAS stack`

## Review fix: pin MinIO images

Replaced the mutable `minio/minio:latest` and `minio/mc:latest` references in
`compose.yaml` with the following tested release tags and registry manifest
digests:

- `minio/minio:RELEASE.2025-09-07T16-13-09Z@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e`
- `minio/mc:RELEASE.2025-08-13T08-35-41Z@sha256:a7fe349ef4bd8521fb8497f55c6042871b2ae640607cf99d9bede5e9bdf11727`

The server image uses the newest MinIO Docker Hub release tag currently
published by the legacy image repository; the client image uses its newest
published release tag. Both manifest digests were read from the locally pulled
images.

### Command results

- `docker compose config --quiet` — passed.
- `docker compose up --build --wait` — passed; PostgreSQL, MinIO, backend,
  caption worker, and frontend were healthy, and `minio-init` completed
  successfully.
- `curl --fail http://localhost:8080/api/health` — passed; returned
  `{"status":"ready"}`.
- `curl --fail http://localhost:8000/health` — passed; returned
  `{"status":"ready"}`.
- `curl --fail http://localhost:5173/` — passed; returned the Vite CANVAS HTML.
- `docker compose down` — passed; named volumes were preserved.
- `git diff --check` — passed.

### Files changed

- `compose.yaml` — replaced both mutable MinIO image references with
  release-tag and digest-pinned image references.
- `.superpowers/sdd/task-1-report.md` — recorded this review fix and its
  verification evidence.

### Commits

- `312f33488ddcf5e09a8f32aeff0efbd111b1915c` — `build: pin MinIO images`
