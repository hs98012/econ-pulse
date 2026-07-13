#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

MYSQL_DATABASE="${MYSQL_DATABASE:-econpulse}"
MYSQL_USER="${MYSQL_USER:-econpulse}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-econpulse}"
SEARCH_TERM="${1:-perf-term-4999}"

mysql_exec() {
  docker compose exec -T mysql mysql \
    --default-character-set=utf8mb4 \
    -u"$MYSQL_USER" \
    -p"$MYSQL_PASSWORD" \
    "$MYSQL_DATABASE"
}

mysql_exec <<'SQL'
CREATE TABLE IF NOT EXISTS perf_term_numbers (
    n INT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

INSERT IGNORE INTO perf_term_numbers (n)
SELECT gen.n
FROM (
    SELECT ones.n + tens.n * 10 + hundreds.n * 100 + thousands.n * 1000 + 1 AS n
    FROM (
        SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    ) ones
    CROSS JOIN (
        SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    ) tens
    CROSS JOIN (
        SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    ) hundreds
    CROSS JOIN (
        SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    ) thousands
) gen
WHERE gen.n <= 5000;

INSERT IGNORE INTO economic_terms (name, normalized_name, definition, status, created_at, updated_at)
SELECT
    CONCAT('perf-term-', n),
    CONCAT('perf-term-', n),
    CONCAT('performance fixture term ', n),
    'ACTIVE',
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6)
FROM perf_term_numbers;

INSERT IGNORE INTO economic_term_aliases (economic_term_id, alias, normalized_alias)
SELECT t.id, CONCAT('perf-alias-a-', p.n), CONCAT('perf-alias-a-', p.n)
FROM perf_term_numbers p
JOIN economic_terms t ON t.normalized_name = CONCAT('perf-term-', p.n);

INSERT IGNORE INTO economic_term_aliases (economic_term_id, alias, normalized_alias)
SELECT t.id, CONCAT('perf-alias-b-', p.n), CONCAT('perf-alias-b-', p.n)
FROM perf_term_numbers p
JOIN economic_terms t ON t.normalized_name = CONCAT('perf-term-', p.n);
SQL

echo "== ACTIVE list query =="
mysql_exec <<'SQL'
EXPLAIN ANALYZE
SELECT DISTINCT t.id, t.name, t.normalized_name, t.definition, t.status, t.created_at, t.updated_at
FROM economic_terms t
WHERE t.status = 'ACTIVE'
ORDER BY t.name ASC, t.id ASC
LIMIT 20 OFFSET 0;
SQL

echo "== Name search query =="
mysql_exec <<SQL
EXPLAIN ANALYZE
SELECT DISTINCT t.id, t.name, t.normalized_name, t.definition, t.status, t.created_at, t.updated_at
FROM economic_terms t
LEFT JOIN economic_term_aliases a ON a.economic_term_id = t.id
WHERE t.status = 'ACTIVE'
AND (
    t.normalized_name LIKE CONCAT('%', '$SEARCH_TERM', '%')
    OR a.normalized_alias LIKE CONCAT('%', '$SEARCH_TERM', '%')
)
ORDER BY t.name ASC, t.id ASC
LIMIT 20 OFFSET 0;
SQL

echo "== Alias search query =="
mysql_exec <<SQL
EXPLAIN ANALYZE
SELECT DISTINCT t.id, t.name, t.normalized_name, t.definition, t.status, t.created_at, t.updated_at
FROM economic_terms t
LEFT JOIN economic_term_aliases a ON a.economic_term_id = t.id
WHERE t.status = 'ACTIVE'
AND (
    t.normalized_name LIKE CONCAT('%', 'perf-alias-b-4999', '%')
    OR a.normalized_alias LIKE CONCAT('%', 'perf-alias-b-4999', '%')
)
ORDER BY t.name ASC, t.id ASC
LIMIT 20 OFFSET 0;
SQL
