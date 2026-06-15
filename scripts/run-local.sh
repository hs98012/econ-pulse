#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

docker compose up -d

if [[ ( -f build.gradle || -f build.gradle.kts ) && -f gradlew ]]; then
  if [[ -x gradlew ]]; then
    exec ./gradlew bootRun
  fi

  echo "Gradle wrapper is not executable; running it with bash."
  exec bash ./gradlew bootRun
fi

echo "Gradle project is not initialized; infrastructure is running."
