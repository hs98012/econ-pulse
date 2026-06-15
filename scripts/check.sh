#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

has_build_file=false
if [[ -f build.gradle || -f build.gradle.kts ]]; then
  has_build_file=true
fi

if [[ "$has_build_file" == true && -f gradlew ]]; then
  if [[ ! -x gradlew ]]; then
    echo "Gradle wrapper is not executable; running it with bash."
    bash ./gradlew test
    bash ./gradlew build
  else
    ./gradlew test
    ./gradlew build
  fi
else
  echo "Gradle project is not initialized; skipping tests and build."
fi
