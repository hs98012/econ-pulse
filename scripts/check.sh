#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.gradle}"

bash -n scripts/*.sh

has_build_file=false
if [[ -f build.gradle || -f build.gradle.kts ]]; then
  has_build_file=true
fi

if [[ "$has_build_file" == true && -f gradlew ]]; then
  if [[ ! -x gradlew ]]; then
    echo "Gradle wrapper is not executable; running it with bash."
    bash ./gradlew clean check
  else
    ./gradlew clean check
  fi
else
  echo "Gradle project is not initialized; skipping tests and build."
fi

docker compose config >/dev/null
