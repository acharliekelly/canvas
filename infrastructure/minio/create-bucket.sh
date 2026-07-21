#!/bin/sh
set -eu

mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
mc mb --ignore-existing "local/$CANVAS_ORIGINALS_BUCKET"
mc mb --ignore-existing local/canvas-generated
mc anonymous set none "local/$CANVAS_ORIGINALS_BUCKET"
mc anonymous set none local/canvas-generated
