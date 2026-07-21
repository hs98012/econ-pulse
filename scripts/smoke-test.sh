#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
READY_TIMEOUT_SECONDS="${READY_TIMEOUT_SECONDS:-60}"
RUN_ID="${SMOKE_RUN_ID:-$$}"

if [[ "${ALLOW_SMOKE_WRITE:-false}" != "true" ]]; then
  echo "This smoke test writes fixture data. Use only on a disposable local database." >&2
  echo "Re-run with ALLOW_SMOKE_WRITE=true after checking BASE_URL=$BASE_URL." >&2
  exit 2
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required." >&2
  exit 1
fi

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/econpulse-smoke.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT
BODY_FILE="$TMP_DIR/body"
HEADER_FILE="$TMP_DIR/headers"
HTTP_STATUS=""
HTTP_BODY=""

request() {
  local method="$1"
  local path="$2"
  local payload="${3:-}"
  local args=(--silent --show-error --connect-timeout 3 --max-time 15
    --request "$method" --dump-header "$HEADER_FILE" --output "$BODY_FILE"
    --write-out '%{http_code}')
  if [[ -n "$payload" ]]; then
    args+=(--header 'Content-Type: application/json' --data "$payload")
  fi
  HTTP_STATUS="$(curl "${args[@]}" "$BASE_URL$path")"
  HTTP_BODY="$(<"$BODY_FILE")"
}

expect_status() {
  local expected="$1"
  local label="$2"
  if [[ "$HTTP_STATUS" != "$expected" ]]; then
    echo "$label failed: expected HTTP $expected, got $HTTP_STATUS." >&2
    echo "Response: ${HTTP_BODY:0:500}" >&2
    exit 1
  fi
}

expect_body() {
  local value="$1"
  local label="$2"
  if ! printf '%s' "$HTTP_BODY" | grep -Fq "$value"; then
    echo "$label failed: response did not contain '$value'." >&2
    echo "Response: ${HTTP_BODY:0:500}" >&2
    exit 1
  fi
}

extract_first_number() {
  local key="$1"
  printf '%s' "$HTTP_BODY" \
    | grep -o "\"${key}\"[[:space:]]*:[[:space:]]*[0-9][0-9]*" \
    | head -n 1 \
    | sed 's/.*:[[:space:]]*//'
}

echo "Waiting for readiness at $BASE_URL (timeout ${READY_TIMEOUT_SECONDS}s)..."
deadline=$((SECONDS + READY_TIMEOUT_SECONDS))
while (( SECONDS < deadline )); do
  request GET /actuator/health/readiness || true
  if [[ "$HTTP_STATUS" == "200" ]] && printf '%s' "$HTTP_BODY" | grep -Fq '"status":"UP"'; then
    break
  fi
  sleep 2
done
if [[ "$HTTP_STATUS" != "200" ]] || ! printf '%s' "$HTTP_BODY" | grep -Fq '"status":"UP"'; then
  echo "Readiness did not become UP before timeout (last HTTP ${HTTP_STATUS:-none})." >&2
  exit 1
fi

for health_path in /actuator/health /actuator/health/liveness /actuator/health/readiness; do
  request GET "$health_path"
  expect_status 200 "$health_path"
  expect_body '"status":"UP"' "$health_path"
  if ! grep -Eiq '^X-Request-Id:[[:space:]]*[^[:space:]]+' "$HEADER_FILE"; then
    echo "$health_path failed: X-Request-Id response header is missing." >&2
    exit 1
  fi
done
if printf '%s' "$HTTP_BODY" | grep -Eq '"components"|"details"'; then
  echo "Public health response exposed component details." >&2
  exit 1
fi

request GET /actuator/metrics
expect_status 404 /actuator/metrics
request GET /actuator/prometheus
expect_status 404 /actuator/prometheus

term_payload="{\"name\":\"기준금리\",\"definition\":\"clean-environment-smoke-${RUN_ID}\",\"aliases\":[\"정책금리\",\"base rate\"]}"
request POST /api/v1/terms "$term_payload"
expect_status 201 "term creation"
expect_body '"name":"기준금리"' "term creation"
TERM_ID="$(extract_first_number id)"
if [[ -z "$TERM_ID" ]]; then
  echo "Term creation failed: response ID is missing." >&2
  exit 1
fi

request GET '/api/v1/terms?page=0&size=20'
expect_status 200 "term list"
expect_body '"name":"기준금리"' "term list"
request GET '/api/v1/terms?query=base%20rate&page=0&size=20'
expect_status 200 "term alias search"
expect_body '"name":"기준금리"' "term alias search"
request GET "/api/v1/terms/$TERM_ID"
expect_status 200 "term detail"
expect_body "\"id\":$TERM_ID" "term detail"

request POST /internal/api/v1/news/sync \
  '{"query":"기준금리","page":0,"size":10,"sort":"RECENCY"}'
expect_status 200 "Fake news sync"
expect_body '"created":2' "Fake news sync"

request GET '/api/v1/news?page=0&size=20'
expect_status 200 "news list"
expect_body '한국은행 기준금리 동결' "news list"
NEWS_ID="$(extract_first_number id)"
if [[ -z "$NEWS_ID" ]]; then
  echo "News list failed: response ID is missing." >&2
  exit 1
fi

request POST "/internal/api/v1/news/$NEWS_ID/term-mappings/auto"
expect_status 200 "automatic term-news mapping"
expect_body '"created":1' "automatic term-news mapping"
request POST "/internal/api/v1/news/$NEWS_ID/term-mappings/auto"
expect_status 200 "idempotent automatic mapping"
expect_body '"skipped":1' "idempotent automatic mapping"

request GET "/api/v1/terms/$TERM_ID/news?page=0&size=20"
expect_status 200 "related news"
expect_body "\"id\":$NEWS_ID" "related news"
expect_body '"totalElements":1' "related news idempotency"

request GET '/api/v1/terms/popular?limit=10'
expect_status 200 "popular terms"
expect_body "\"economicTermId\":$TERM_ID" "popular terms"
POPULAR_SCORE="$(extract_first_number score)"
if [[ -z "$POPULAR_SCORE" ]] || (( POPULAR_SCORE < 1 )); then
  echo "Popular term score was not recorded." >&2
  exit 1
fi
expect_body '"rank":1' "popular term rank"

echo "Smoke test passed: health, term CRUD/search, Fake ingestion, idempotent mapping, related news, and popular score."
