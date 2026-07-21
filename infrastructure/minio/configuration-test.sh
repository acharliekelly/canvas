#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
test_bucket=canvas-config-test
test_swallow_size=20MB

CANVAS_ORIGINALS_BUCKET="$test_bucket" CANVAS_MAX_SWALLOW_SIZE="$test_swallow_size" \
    docker compose --project-directory "$repository_root" \
    -f "$repository_root/compose.yaml" config --format json \
    | node -e '
let input = "";
process.stdin.on("data", chunk => input += chunk);
process.stdin.on("end", () => {
  const services = JSON.parse(input).services;
  const expected = "canvas-config-test";
  const expectedSwallowSize = "20MB";
  if (services.backend.environment.CANVAS_ORIGINALS_BUCKET !== expected
      || services["minio-init"].environment.CANVAS_ORIGINALS_BUCKET !== expected
      || services.backend.environment.CANVAS_MAX_SWALLOW_SIZE !== expectedSwallowSize) {
    process.exit(1);
  }
});'

test_directory=$(mktemp -d)
trap 'rm -rf "$test_directory"' EXIT
calls="$test_directory/mc-calls"
printf '%s\n' '#!/bin/sh' 'printf "%s\n" "$*" >> "$MC_CALLS"' > "$test_directory/mc"
chmod +x "$test_directory/mc"

PATH="$test_directory:$PATH" MC_CALLS="$calls" MINIO_ROOT_USER=test MINIO_ROOT_PASSWORD=test-password \
    CANVAS_ORIGINALS_BUCKET="$test_bucket" sh "$repository_root/infrastructure/minio/create-bucket.sh"

grep -Fx "mb --ignore-existing local/$test_bucket" "$calls" >/dev/null
grep -Fx "anonymous set none local/$test_bucket" "$calls" >/dev/null
