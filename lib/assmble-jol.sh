#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
OUT_DIR="${ROOT_DIR}/lib/jol-dist"

mkdir -p "${OUT_DIR}"

docker build \
  --file "${ROOT_DIR}/lib/jolDockerFile" \
  --target export \
  --output "type=local,dest=${OUT_DIR}" \
  "${ROOT_DIR}"

echo "JOL artifacts exported to ${OUT_DIR}"
