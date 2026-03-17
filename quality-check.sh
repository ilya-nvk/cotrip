#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "[quality-check] backend"
(cd "$ROOT_DIR/backend" && ./gradlew qualityCheck)

echo "[quality-check] android"
(cd "$ROOT_DIR/android" && ./gradlew :app:qualityCheck)

echo "[quality-check] done"
