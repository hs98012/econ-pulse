#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

MYSQL_DATABASE="${MYSQL_DATABASE:-econpulse_query_analysis}"
MYSQL_USER="${MYSQL_USER:-econpulse}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-econpulse}"

if [[ "${ANALYZE_QUERY_PLANS_CONFIRM:-}" != "local" ]]; then
  echo "Refusing to seed data. Run only against a disposable local database with:" >&2
  echo "ANALYZE_QUERY_PLANS_CONFIRM=local MYSQL_DATABASE=econpulse_query_analysis $0" >&2
  exit 2
fi

if [[ "$MYSQL_DATABASE" != *"analysis"* ]]; then
  echo "MYSQL_DATABASE must contain 'analysis'; never run this script against an operational database." >&2
  exit 2
fi

docker compose exec -T mysql mysql \
  --default-character-set=utf8mb4 \
  -u"$MYSQL_USER" \
  -p"$MYSQL_PASSWORD" \
  "$MYSQL_DATABASE" < scripts/sql/query-plan-analysis.sql
