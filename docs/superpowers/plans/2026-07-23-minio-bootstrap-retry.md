# MinIO Bootstrap Retry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the local MinIO initializer tolerate transient Docker DNS or readiness failures without hiding persistent initialization errors.

**Architecture:** Keep retry behavior inside the one-shot MinIO bootstrap script and apply it only to the authenticated `mc alias set` boundary. Extend the existing shell-based infrastructure test with deterministic fake `mc` and `sleep` executables so both recovery and exhaustion are tested without real delays.

**Tech Stack:** POSIX shell, Docker Compose, MinIO Client (`mc`), GNU Make

## Global Constraints

- Make at most 10 authenticated alias attempts.
- Wait one second only between failed attempts.
- Continue immediately after the first successful alias attempt.
- Exit nonzero after the tenth failed attempt.
- Do not retry bucket, anonymous-access, user, or policy operations.
- Do not reset or remove local Compose volumes.
- Preserve private buckets and the existing least-privilege backend policy.

---

### Task 1: Test and Implement Bounded MinIO Connection Retries

**Files:**
- Modify: `infrastructure/minio/configuration-test.sh`
- Modify: `infrastructure/minio/create-bucket.sh`

**Interfaces:**
- Consumes: the existing MinIO environment variables and `mc alias set local http://minio:9000 USER PASSWORD` command.
- Produces: a bootstrap script that retries only the alias command up to 10 times with one-second intervals.

- [ ] **Step 1: Extend the fake commands to model transient alias failures**

Replace the current fake `mc` setup in `infrastructure/minio/configuration-test.sh` with:

```sh
calls="$test_directory/mc-calls"
policy="$test_directory/policy.json"
sleeps="$test_directory/sleep-calls"
printf '%s\n' '#!/bin/sh' \
  'printf "%s\n" "$*" >> "$MC_CALLS"' \
  'if [ "$1 $2" = "alias set" ]; then' \
  '  attempt=$(grep -c "^alias set " "$MC_CALLS")' \
  '  if [ "$MC_ALIAS_SUCCEEDS_ON_ATTEMPT" -eq 0 ] || [ "$attempt" -lt "$MC_ALIAS_SUCCEEDS_ON_ATTEMPT" ]; then exit 1; fi' \
  'fi' \
  'if [ "$1 $2 $3" = "admin policy create" ]; then cp "$6" "$MC_POLICY"; fi' > "$test_directory/mc"
printf '%s\n' '#!/bin/sh' \
  'printf "%s\n" "$*" >> "$SLEEP_CALLS"' > "$test_directory/sleep"
chmod +x "$test_directory/mc" "$test_directory/sleep"
```

Run the initializer with `MC_ALIAS_SUCCEEDS_ON_ATTEMPT=3` and `SLEEP_CALLS="$sleeps"`:

```sh
PATH="$test_directory:$PATH" MC_CALLS="$calls" MC_POLICY="$policy" SLEEP_CALLS="$sleeps" \
    MC_ALIAS_SUCCEEDS_ON_ATTEMPT=3 MINIO_ROOT_USER=test MINIO_ROOT_PASSWORD=test-password \
    CANVAS_ORIGINALS_BUCKET="$test_originals_bucket" CANVAS_GENERATED_BUCKET="$test_generated_bucket" \
    CANVAS_S3_ACCESS_KEY="$test_access_key" CANVAS_S3_SECRET_KEY="$test_secret_key" \
    sh "$repository_root/infrastructure/minio/create-bucket.sh"
```

Add assertions immediately after that invocation:

```sh
test "$(grep -c '^alias set local http://minio:9000 test test-password$' "$calls")" -eq 3
test "$(grep -c '^1$' "$sleeps")" -eq 2
test "$(grep -c "^mb --ignore-existing local/$test_originals_bucket$" "$calls")" -eq 1
test "$(grep -c "^mb --ignore-existing local/$test_generated_bucket$" "$calls")" -eq 1
```

- [ ] **Step 2: Add an exhaustion test**

After the existing policy assertions, clear the fake-command records and run the initializer with an alias that never succeeds:

```sh
: > "$calls"
: > "$sleeps"
if PATH="$test_directory:$PATH" MC_CALLS="$calls" MC_POLICY="$policy" SLEEP_CALLS="$sleeps" \
    MC_ALIAS_SUCCEEDS_ON_ATTEMPT=0 MINIO_ROOT_USER=test MINIO_ROOT_PASSWORD=test-password \
    CANVAS_ORIGINALS_BUCKET="$test_originals_bucket" CANVAS_GENERATED_BUCKET="$test_generated_bucket" \
    CANVAS_S3_ACCESS_KEY="$test_access_key" CANVAS_S3_SECRET_KEY="$test_secret_key" \
    sh "$repository_root/infrastructure/minio/create-bucket.sh"; then
    printf '%s\n' "Expected persistent MinIO alias failure to abort bootstrap." >&2
    exit 1
fi
test "$(grep -c '^alias set local http://minio:9000 test test-password$' "$calls")" -eq 10
test "$(grep -c '^1$' "$sleeps")" -eq 9
if grep -q '^mb ' "$calls"; then
    printf '%s\n' "Bucket initialization ran after MinIO alias failure." >&2
    exit 1
fi
```

- [ ] **Step 3: Run the infrastructure test to verify RED**

Run:

```bash
sh infrastructure/minio/configuration-test.sh
```

Expected: exit 1 during the first fake alias attempt because `create-bucket.sh` does not retry yet.

- [ ] **Step 4: Implement the minimal bounded retry**

Replace the single alias command in `infrastructure/minio/create-bucket.sh` with:

```sh
max_alias_attempts=10
alias_attempt=1
until mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"; do
    if [ "$alias_attempt" -ge "$max_alias_attempts" ]; then
        printf '%s\n' "MinIO bootstrap could not connect after $max_alias_attempts attempts." >&2
        exit 1
    fi
    printf '%s\n' "MinIO bootstrap connection attempt $alias_attempt failed; retrying in one second." >&2
    alias_attempt=$((alias_attempt + 1))
    sleep 1
done
```

Leave every command after the alias loop unchanged.

- [ ] **Step 5: Run the infrastructure test to verify GREEN**

Run:

```bash
sh infrastructure/minio/configuration-test.sh
```

Expected: exit 0. The recovery case records three alias attempts and two sleeps; the exhaustion case records ten alias attempts and nine sleeps without invoking bucket setup.

- [ ] **Step 6: Run the complete non-GPU verification suite**

Run:

```bash
make verify
```

Expected: exit 0 with backend, frontend, worker, infrastructure, and Compose checks passing. A non-fatal axe preload warning may appear on stderr, but the Vitest and Make exit statuses must remain zero.

- [ ] **Step 7: Run the vertical workflow**

Run:

```bash
make e2e
```

Expected: exit 0 after all Compose services become ready and the Playwright publication and automated accessibility journeys pass. The target stops containers while preserving named volumes.

- [ ] **Step 8: Verify the final diff and repository state**

Run:

```bash
git diff --check
git status --short --branch
git diff -- infrastructure/minio/create-bucket.sh infrastructure/minio/configuration-test.sh
```

Expected: no whitespace errors; only the two infrastructure files are uncommitted; `main` remains ahead of `origin/main`.

- [ ] **Step 9: Commit the tested fix**

```bash
git add infrastructure/minio/create-bucket.sh infrastructure/minio/configuration-test.sh
git commit -m "fix: retry transient MinIO bootstrap failures"
```
