# Java Namespace Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the backend's former placeholder Java namespace with the canonical `me.acharliekelly.canvas` namespace everywhere in the repository.

**Architecture:** Move the complete production and test package trees atomically, then apply one mechanical namespace replacement across tracked text files. Preserve Spring Boot scanning, Spring Modulith boundaries, runtime contracts, database state, and non-Java components by changing only Java package identity, Maven coordinates, and references to them.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Modulith 2.1, Maven, Git, Docker Compose, Node.js 24, Python 3.13.

## Global Constraints

- The canonical Java package and Maven group ID are `me.acharliekelly.canvas`.
- Change every repository occurrence of the former dotted namespace and former slash-delimited package path, including historical documentation.
- Do not rename classes, modules, endpoints, database objects, configuration keys, Docker services, JavaScript or Python packages, or Maven artifact ID `canvas-backend`.
- Do not add compatibility aliases, deprecation bridges, data migrations, dependencies, or an ADR.
- Preserve runtime behavior and all HTTP, persistence, storage, publication, and worker contracts.
- Update pull request #4 by committing and pushing to `docs/code-documentation`.

---

### Task 1: Migrate the complete backend namespace

**Files:**
- Move: the former production package tree, resolved by constructing `FORMER_PATH` from separate namespace segments, to `backend/src/main/java/me/acharliekelly/canvas/`
- Move: the former test package tree, resolved by constructing `FORMER_PATH` from separate namespace segments, to `backend/src/test/java/me/acharliekelly/canvas/`
- Modify: `backend/pom.xml`
- Modify: every tracked text file containing the former dotted namespace or package path
- Modify: `docs/superpowers/specs/2026-07-23-java-namespace-migration-design.md`
- Modify: `docs/superpowers/plans/2026-07-23-java-namespace-migration.md`

**Interfaces:**
- Produces: Java root package `me.acharliekelly.canvas`
- Produces: Maven coordinate `me.acharliekelly.canvas:canvas-backend:0.0.1-SNAPSHOT`
- Preserves: every class name, subpackage boundary, API route, persistence mapping, and runtime contract

- [ ] **Step 1: Record the clean baseline and construct the former namespace without leaving a literal in the plan**

Run:

```bash
git status --short
git branch --show-current
FORMER_DOTTED=$(printf '%s.%s' org canvas)
FORMER_PATH=$(printf '%s/%s' org canvas)
rg -l --hidden --glob '!.git/**' --glob '!**/target/**' \
  --glob '!**/node_modules/**' --glob '!**/.venv/**' \
  -F -e "$FORMER_DOTTED" -e "$FORMER_PATH" .
```

Expected: the worktree is clean, the branch is `docs/code-documentation`, and matches are limited to backend Java/Maven files plus documentation.

- [ ] **Step 2: Establish a failing migration-completeness check**

Run:

```bash
FORMER_DOTTED=$(printf '%s.%s' org canvas)
FORMER_PATH=$(printf '%s/%s' org canvas)
test ! -d "backend/src/main/java/$FORMER_PATH"
test ! -d "backend/src/test/java/$FORMER_PATH"
! rg -n --hidden --glob '!.git/**' --glob '!**/target/**' \
  --glob '!**/node_modules/**' --glob '!**/.venv/**' \
  -F -e "$FORMER_DOTTED" -e "$FORMER_PATH" .
```

Expected before implementation: FAIL because both former source trees and textual references still exist.

- [ ] **Step 3: Move both Java trees**

Run:

```bash
mkdir -p backend/src/main/java/me/acharliekelly
mkdir -p backend/src/test/java/me/acharliekelly
FORMER_PATH=$(printf '%s/%s' org canvas)
git mv "backend/src/main/java/$FORMER_PATH" backend/src/main/java/me/acharliekelly/canvas
git mv "backend/src/test/java/$FORMER_PATH" backend/src/test/java/me/acharliekelly/canvas
rmdir backend/src/main/java/org backend/src/test/java/org
```

Expected: Git records every Java file as a move into the canonical package tree, with no former package directory remaining.

- [ ] **Step 4: Replace tracked textual references mechanically**

Run this repository-scoped bulk rewrite:

```bash
FORMER_DOTTED=$(printf '%s.%s' org canvas)
FORMER_PATH=$(printf '%s/%s' org canvas)
rg -l -0 --hidden --glob '!.git/**' --glob '!**/target/**' \
  --glob '!**/node_modules/**' --glob '!**/.venv/**' \
  -F -e "$FORMER_DOTTED" -e "$FORMER_PATH" . \
  | xargs -0 sed -i \
      -e "s/$FORMER_DOTTED/me.acharliekelly.canvas/g" \
      -e "s|$FORMER_PATH|me/acharliekelly/canvas|g"
```

Expected: Java declarations/imports, Maven `groupId`, and all current/historical document paths use the canonical namespace.

- [ ] **Step 5: Repair migration-document wording after the mechanical rewrite**

In the design and this implementation plan, ensure the completed migration is described as replacing a "former placeholder namespace" rather than containing nonsensical statements that replace the canonical namespace with itself. Keep commands capable of checking the former value by constructing it from separate `org` and `canvas` tokens, so no complete old occurrence remains.

Expected: both documents remain truthful and readable after the repository-wide replacement.

- [ ] **Step 6: Prove migration completeness**

Run:

```bash
FORMER_DOTTED=$(printf '%s.%s' org canvas)
FORMER_PATH=$(printf '%s/%s' org canvas)
test ! -d "backend/src/main/java/$FORMER_PATH"
test ! -d "backend/src/test/java/$FORMER_PATH"
test -f backend/src/main/java/me/acharliekelly/canvas/CanvasApplication.java
test -f backend/src/test/java/me/acharliekelly/canvas/architecture/ModuleBoundariesTest.java
! rg -n --hidden --glob '!.git/**' --glob '!**/target/**' \
  --glob '!**/node_modules/**' --glob '!**/.venv/**' \
  -F -e "$FORMER_DOTTED" -e "$FORMER_PATH" .
rg -n "me\\.acharliekelly\\.canvas|me/acharliekelly/canvas" \
  backend/pom.xml backend/src docs
```

Expected: no former directory or textual reference exists; the application, architecture test, Maven coordinate, source code, tests, and documentation use the canonical namespace.

- [ ] **Step 7: Run focused Java verification**

Run:

```bash
cd backend
./mvnw -q -DskipTests compile
./mvnw -q -DskipTests javadoc:javadoc
./mvnw test -Dtest=ModuleBoundariesTest,MigrationFromEmptyTest,SchemaValidationStartupTest
```

Expected: compilation and JavaDoc generation succeed; the architecture and migration checks pass under the new root package.

- [ ] **Step 8: Run complete repository verification**

Run with Node 24 and the documented Python 3.13 worker environment:

```bash
PATH=/home/charlie/.nvm/versions/node/v24.18.0/bin:$PATH make verify
git diff --check
```

If the host worker environment is unavailable, run the worker suite through its test image without narrowing the suite:

```bash
docker build --target test -t canvas-worker-test caption-worker
docker run --rm canvas-worker-test python -m pytest -q
```

Expected: backend 101 tests, frontend 51 tests plus lint/typecheck/build, worker 6 tests, MinIO and Compose checks, JavaDoc, and whitespace validation pass.

- [ ] **Step 9: Review scope and commit**

Run:

```bash
git status --short
git diff --stat
git diff -- backend/pom.xml \
  backend/src/main/java/me/acharliekelly/canvas/CanvasApplication.java \
  backend/src/test/java/me/acharliekelly/canvas/architecture/ModuleBoundariesTest.java
```

Confirm the diff contains only source/test moves, namespace substitutions, Maven `groupId`, and migration-document wording.

Commit:

```bash
git add backend docs
git commit -m "refactor: correct Java namespace"
```

- [ ] **Step 10: Update pull request #4**

Run:

```bash
git push origin docs/code-documentation
```

Update the pull request description to mention the canonical Java namespace migration and its full verification evidence.
