# Java 25 Backend Health-Check Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the backend container's HTTP readiness check after the Java 25 runtime-image upgrade.

**Architecture:** Make the existing `wget` health-check dependency explicit in the backend runtime image. Preserve the current Compose command and `/api/health` readiness contract, and verify the dependency directly before rerunning the combined stack.

**Tech Stack:** Docker, Eclipse Temurin 25 JRE, Debian/Ubuntu `apt`, Docker Compose, Spring Boot Actuator

## Global Constraints

- Keep Java 25 as the backend build and runtime baseline.
- Preserve the existing `/api/health` readiness semantics.
- Do not change application APIs, persistence, or editorial behavior.
- Install only `ca-certificates` and `wget` without recommended packages.
- Remove package-manager indexes in the same Docker layer.

---

### Task 1: Make the Backend Health-Check Client Explicit

**Files:**
- Modify: `backend/Dockerfile`
- Test: built `canvas-backend-healthcheck-test` runtime image

**Interfaces:**
- Consumes: Compose health check `wget -qO- http://localhost:8080/api/health`.
- Produces: a Java 25 backend runtime image containing `wget` and trusted CA certificates.

- [ ] **Step 1: Reproduce the missing runtime executable**

Build the unchanged runtime image and invoke the health-check client directly:

```bash
docker build -t canvas-backend-healthcheck-test backend
docker run --rm --entrypoint wget canvas-backend-healthcheck-test --version
```

Expected: the build succeeds; the run exits nonzero because `wget` is absent.

- [ ] **Step 2: Install the explicit runtime dependencies**

Add this layer immediately after the final `FROM eclipse-temurin:25-jre` line in `backend/Dockerfile`:

```dockerfile
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates wget \
    && rm -rf /var/lib/apt/lists/*
```

Leave the build stage, application copy, exposed port, and entry point unchanged.

- [ ] **Step 3: Verify the runtime dependency**

Rebuild and invoke `wget`:

```bash
docker build -t canvas-backend-healthcheck-test backend
docker run --rm --entrypoint wget canvas-backend-healthcheck-test --version
```

Expected: both commands exit 0 and `wget` prints its version.

- [ ] **Step 4: Run the focused MinIO infrastructure regression test**

Run:

```bash
sh infrastructure/minio/configuration-test.sh
```

Expected: exit 0, including transient recovery and ten-attempt exhaustion.

- [ ] **Step 5: Recreate the isolated Compose network**

Remove only the isolated fix project's containers and network while preserving named volumes:

```bash
docker compose -p minio-bootstrap-retry down
```

Expected: containers and `minio-bootstrap-retry_default` are removed; the PostgreSQL and MinIO named volumes are retained.

- [ ] **Step 6: Run the combined vertical workflow**

Run:

```bash
make e2e
```

Expected: MinIO initialization completes, the backend health check reaches `healthy`, the frontend starts, and all Playwright publication and automated accessibility journeys pass.

- [ ] **Step 7: Verify the complete available test surface**

Run:

```bash
cd backend && ./mvnw verify
cd ../frontend && npm ci && npm run lint && npm run typecheck && npm test -- --run && npm run build
cd .. && make test-worker test-infrastructure
```

Expected: backend reports 101 tests with zero failures/errors; frontend reports 51 tests with lint, typecheck, and build passing; the Dockerized Python 3.13 worker test and infrastructure checks pass.

The top-level `make verify` remains environment-blocked if host Python 3.13 and the worker test extras are unavailable. The Dockerized worker target provides the same worker test coverage without weakening the result.

- [ ] **Step 8: Verify the final diff**

Run:

```bash
git diff --check
git status --short --branch
git diff -- backend/Dockerfile infrastructure/minio/create-bucket.sh infrastructure/minio/configuration-test.sh
```

Expected: no whitespace errors; the Dockerfile and two MinIO infrastructure files contain the implementation changes.

- [ ] **Step 9: Commit the Java 25 compatibility fix**

```bash
git add backend/Dockerfile
git commit -m "fix: restore backend container health check"
```

- [ ] **Step 10: Commit the MinIO retry fix**

```bash
git add infrastructure/minio/create-bucket.sh infrastructure/minio/configuration-test.sh
git commit -m "fix: retry transient MinIO bootstrap failures"
```
