#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.gradle}"

if [[ ! -x gradlew ]]; then
  GRADLE_CMD=(bash ./gradlew)
else
  GRADLE_CMD=(./gradlew)
fi

"${GRADLE_CMD[@]}" bootRun \
  --args='--spring.profiles.active=local --econpulse.seed.enabled=true --econpulse.seed.exit-after-run=true --server.port=0'
