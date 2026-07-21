#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ "${ALLOW_DATA_RESET:-false}" != "true" ]]; then
  echo "Refusing to delete local MySQL and Redis volumes." >&2
  echo "Re-run with ALLOW_DATA_RESET=true after checking the Compose project name." >&2
  exit 2
fi

PROJECT_NAME="${COMPOSE_PROJECT_NAME:-econ-pulse}"
if [[ ! "$PROJECT_NAME" =~ ^[a-z0-9][a-z0-9_-]*$ ]]; then
  echo "Unsafe Compose project name: $PROJECT_NAME" >&2
  exit 2
fi

echo "WARNING: deleting MySQL and Redis volumes only for Compose project '$PROJECT_NAME'." >&2
docker compose -p "$PROJECT_NAME" down -v --remove-orphans
docker compose -p "$PROJECT_NAME" up -d --wait --wait-timeout 90
