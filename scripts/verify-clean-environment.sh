#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_NAME="${CLEAN_COMPOSE_PROJECT:-econpulse-clean-$$}"
MYSQL_PORT="${CLEAN_MYSQL_PORT:-33308}"
REDIS_PORT="${CLEAN_REDIS_PORT:-36379}"
SERVER_PORT="${CLEAN_SERVER_PORT:-38080}"
TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/econpulse-clean.XXXXXX")"
WORK_DIR="$TEMP_DIR/repository"
APP_LOG="$TEMP_DIR/application.log"
RESTART_LOG="$TEMP_DIR/application-restart.log"
APP_PID=""
SUCCESS=false

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if [[ -n "$APP_PID" ]] && kill -0 "$APP_PID" 2>/dev/null; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
  fi
  if [[ -d "$WORK_DIR" ]]; then
    MYSQL_PORT="$MYSQL_PORT" REDIS_PORT="$REDIS_PORT" \
      docker compose -f "$WORK_DIR/docker-compose.yml" -p "$PROJECT_NAME" \
      down -v --remove-orphans >/dev/null 2>&1 || true
  fi
  if [[ "$SUCCESS" == "true" ]]; then
    rm -rf "$TEMP_DIR"
  else
    echo "Clean verification failed; application logs remain in $TEMP_DIR" >&2
  fi
  exit "$status"
}
trap cleanup EXIT INT TERM

for command_name in java docker curl tar; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "$command_name is required." >&2
    exit 1
  fi
done
if ! docker compose version >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
  echo "A running Docker daemon with the Compose plugin is required." >&2
  exit 1
fi
JAVA_VERSION="$(java -version 2>&1 | awk -F '"' '/version/ {print $2; exit}')"
if [[ "${JAVA_VERSION%%.*}" != "17" ]]; then
  echo "Java 17 is required; detected ${JAVA_VERSION:-unknown}." >&2
  exit 1
fi
if [[ ! "$PROJECT_NAME" =~ ^[a-z0-9][a-z0-9_-]*$ ]]; then
  echo "Unsafe Compose project name: $PROJECT_NAME" >&2
  exit 2
fi

mkdir -p "$WORK_DIR"
tar -C "$ROOT_DIR" --exclude=.git --exclude=.gradle --exclude=build -cf - . \
  | tar -C "$WORK_DIR" -xf -

export MYSQL_HOST=localhost
export MYSQL_PORT
export MYSQL_DATABASE=econpulse_clean
export MYSQL_USER=econpulse_clean
export MYSQL_PASSWORD=clean-placeholder
export MYSQL_ROOT_PASSWORD=clean-root-placeholder
export REDIS_HOST=localhost
export REDIS_PORT
export SERVER_PORT
export SPRING_PROFILES_ACTIVE=local
export ECONPULSE_NEWS_PROVIDER_TYPE=fake
export ECONPULSE_INTERNAL_NEWS_SYNC_ENABLED=true
export ECONPULSE_INTERNAL_MAPPING_REBUILD_ENABLED=false
export ECONPULSE_INTERNAL_TERM_NEWS_MAPPING_ENABLED=true
export GRADLE_USER_HOME="$TEMP_DIR/gradle-home"

echo "Using isolated Compose project '$PROJECT_NAME' and temporary workspace $WORK_DIR"
docker compose -f "$WORK_DIR/docker-compose.yml" -p "$PROJECT_NAME" \
  up -d --wait --wait-timeout 120

start_application() {
  local log_file="$1"
  (
    cd "$WORK_DIR"
    exec ./gradlew bootRun --no-daemon
  ) >"$log_file" 2>&1 &
  APP_PID=$!
}

wait_for_readiness() {
  local deadline=$((SECONDS + 180))
  while (( SECONDS < deadline )); do
    if ! kill -0 "$APP_PID" 2>/dev/null; then
      echo "Application exited before readiness; see $1" >&2
      return 1
    fi
    if curl --silent --fail --connect-timeout 2 --max-time 3 \
      "http://localhost:$SERVER_PORT/actuator/health/readiness" \
      | grep -Fq '"status":"UP"'; then
      return 0
    fi
    sleep 2
  done
  echo "Application readiness timed out; see $1" >&2
  return 1
}

stop_application() {
  if [[ -n "$APP_PID" ]] && kill -0 "$APP_PID" 2>/dev/null; then
    kill "$APP_PID"
    wait "$APP_PID" 2>/dev/null || true
  fi
  APP_PID=""
}

start_application "$APP_LOG"
wait_for_readiness "$APP_LOG"
ALLOW_SMOKE_WRITE=true BASE_URL="http://localhost:$SERVER_PORT" \
  "$WORK_DIR/scripts/smoke-test.sh"

MIGRATION_COUNT="$(docker compose -f "$WORK_DIR/docker-compose.yml" -p "$PROJECT_NAME" \
  exec -T mysql mysql --batch --skip-column-names \
  -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" \
  -e 'SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1;')"
if [[ "$MIGRATION_COUNT" != "2" ]]; then
  echo "Expected 2 successful Flyway migrations, got $MIGRATION_COUNT." >&2
  exit 1
fi

stop_application
start_application "$RESTART_LOG"
wait_for_readiness "$RESTART_LOG"
MIGRATION_COUNT_AFTER_RESTART="$(docker compose -f "$WORK_DIR/docker-compose.yml" -p "$PROJECT_NAME" \
  exec -T mysql mysql --batch --skip-column-names \
  -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" \
  -e 'SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1;')"
if [[ "$MIGRATION_COUNT_AFTER_RESTART" != "$MIGRATION_COUNT" ]]; then
  echo "Flyway history changed unexpectedly after restart." >&2
  exit 1
fi

if grep -Eiq 'naver.*(openapi|client)' "$APP_LOG" "$RESTART_LOG"; then
  echo "Unexpected Naver provider activity was found in application logs." >&2
  exit 1
fi

stop_application
SUCCESS=true
echo "Clean environment verification passed; isolated containers, volumes, and temporary files will be removed."
