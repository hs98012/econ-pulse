#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  . "$ROOT_DIR/.env"
  set +a
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Java 17 is required." >&2
  exit 1
fi

JAVA_VERSION="$(java -version 2>&1 | awk -F '"' '/version/ {print $2; exit}')"
JAVA_MAJOR="${JAVA_VERSION%%.*}"
if [[ "$JAVA_MAJOR" != "17" ]]; then
  echo "Java 17 is required; detected ${JAVA_VERSION:-unknown}." >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  echo "Docker with the Compose plugin is required." >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "Docker is installed but the daemon is not available." >&2
  exit 1
fi

export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.gradle}"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"

echo "Starting MySQL and Redis and waiting for their health checks..."
docker compose up -d --wait --wait-timeout 90
echo "Starting EconPulse in the foreground on port ${SERVER_PORT:-8080}."
echo "Press Ctrl-C to stop the application; run 'docker compose down' to stop infrastructure."

if [[ ( -f build.gradle || -f build.gradle.kts ) && -f gradlew ]]; then
  if [[ -x gradlew ]]; then
    exec ./gradlew bootRun
  fi

  echo "Gradle wrapper is not executable; running it with bash."
  exec bash ./gradlew bootRun
fi

echo "Gradle project is not initialized; infrastructure is running."
