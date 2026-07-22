#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
test_originals_bucket=canvas-config-originals-test
test_generated_bucket=canvas-config-generated-test
test_swallow_size=20MB
test_access_key=canvas-test-app
test_secret_key=canvas-test-app-secret

CANVAS_ORIGINALS_BUCKET="$test_originals_bucket" CANVAS_GENERATED_BUCKET="$test_generated_bucket" \
    CANVAS_S3_ACCESS_KEY="$test_access_key" CANVAS_S3_SECRET_KEY="$test_secret_key" \
    CANVAS_MAX_SWALLOW_SIZE="$test_swallow_size" \
    docker compose --project-directory "$repository_root" \
    -f "$repository_root/compose.yaml" config --format json \
    | node -e '
let input = "";
process.stdin.on("data", chunk => input += chunk);
process.stdin.on("end", () => {
  const services = JSON.parse(input).services;
  const originalsBucket = "canvas-config-originals-test";
  const generatedBucket = "canvas-config-generated-test";
  const accessKey = "canvas-test-app";
  const secretKey = "canvas-test-app-secret";
  const expectedSwallowSize = "20MB";
  const loopbackPorts = ["minio", "caption-worker", "backend", "frontend"];
  if (services.backend.environment.CANVAS_ORIGINALS_BUCKET !== originalsBucket
      || services.backend.environment.CANVAS_GENERATED_BUCKET !== generatedBucket
      || services.backend.environment.CANVAS_S3_ACCESS_KEY !== accessKey
      || services.backend.environment.CANVAS_S3_SECRET_KEY !== secretKey
      || services.backend.environment.MINIO_ROOT_USER !== undefined
      || services.backend.environment.MINIO_ROOT_PASSWORD !== undefined
      || services["minio-init"].environment.CANVAS_ORIGINALS_BUCKET !== originalsBucket
      || services["minio-init"].environment.CANVAS_GENERATED_BUCKET !== generatedBucket
      || services["minio-init"].environment.CANVAS_S3_ACCESS_KEY !== accessKey
      || services["minio-init"].environment.CANVAS_S3_SECRET_KEY !== secretKey
      || services.backend.environment.CANVAS_MAX_SWALLOW_SIZE !== expectedSwallowSize) {
    process.exit(1);
  }
  if (!loopbackPorts.every(name => services[name].ports.every(port => port.host_ip === "127.0.0.1"))) {
    process.exit(1);
  }
});'

test_directory=$(mktemp -d)
trap 'rm -rf "$test_directory"' EXIT
calls="$test_directory/mc-calls"
policy="$test_directory/policy.json"
printf '%s\n' '#!/bin/sh' \
  'printf "%s\n" "$*" >> "$MC_CALLS"' \
  'if [ "$1 $2 $3" = "admin policy create" ]; then cp "$6" "$MC_POLICY"; fi' > "$test_directory/mc"
chmod +x "$test_directory/mc"

PATH="$test_directory:$PATH" MC_CALLS="$calls" MC_POLICY="$policy" MINIO_ROOT_USER=test MINIO_ROOT_PASSWORD=test-password \
    CANVAS_ORIGINALS_BUCKET="$test_originals_bucket" CANVAS_GENERATED_BUCKET="$test_generated_bucket" \
    CANVAS_S3_ACCESS_KEY="$test_access_key" CANVAS_S3_SECRET_KEY="$test_secret_key" \
    sh "$repository_root/infrastructure/minio/create-bucket.sh"

grep -Fx "mb --ignore-existing local/$test_originals_bucket" "$calls" >/dev/null
grep -Fx "mb --ignore-existing local/$test_generated_bucket" "$calls" >/dev/null
grep -Fx "anonymous set none local/$test_originals_bucket" "$calls" >/dev/null
grep -Fx "anonymous set none local/$test_generated_bucket" "$calls" >/dev/null
grep -Fx "admin user add local $test_access_key $test_secret_key" "$calls" >/dev/null
grep -Fx "admin policy create local canvas-backend-policy /tmp/canvas-backend-policy.json" "$calls" >/dev/null
grep -Fx "admin policy attach local canvas-backend-policy --user $test_access_key" "$calls" >/dev/null
grep -F '"s3:GetBucketLocation", "s3:ListBucket"' "$policy" >/dev/null
grep -F '"s3:GetObject", "s3:PutObject", "s3:DeleteObject"' "$policy" >/dev/null
grep -F "arn:aws:s3:::$test_originals_bucket" "$policy" >/dev/null
grep -F "arn:aws:s3:::$test_generated_bucket" "$policy" >/dev/null
if grep -F '"Action": "*"' "$policy" >/dev/null || grep -F '"Resource": "*"' "$policy" >/dev/null; then
    exit 1
fi
