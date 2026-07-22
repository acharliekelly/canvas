#!/bin/sh
set -eu

mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
mc mb --ignore-existing "local/$CANVAS_ORIGINALS_BUCKET"
mc mb --ignore-existing "local/$CANVAS_GENERATED_BUCKET"
mc anonymous set none "local/$CANVAS_ORIGINALS_BUCKET"
mc anonymous set none "local/$CANVAS_GENERATED_BUCKET"
mc admin user add local "$CANVAS_S3_ACCESS_KEY" "$CANVAS_S3_SECRET_KEY"

policy_file=/tmp/canvas-backend-policy.json
trap 'rm -f "$policy_file"' EXIT
cat > "$policy_file" <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetBucketLocation", "s3:ListBucket"],
      "Resource": [
        "arn:aws:s3:::$CANVAS_ORIGINALS_BUCKET",
        "arn:aws:s3:::$CANVAS_GENERATED_BUCKET"
      ]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
      "Resource": [
        "arn:aws:s3:::$CANVAS_ORIGINALS_BUCKET/*",
        "arn:aws:s3:::$CANVAS_GENERATED_BUCKET/*"
      ]
    }
  ]
}
EOF
mc admin policy create local canvas-backend-policy "$policy_file"
mc admin policy attach local canvas-backend-policy --user "$CANVAS_S3_ACCESS_KEY"
