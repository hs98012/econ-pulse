CREATE TABLE IF NOT EXISTS perf_numbers (
    n INT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

INSERT IGNORE INTO perf_numbers (n)
SELECT ones.n + tens.n * 10 + hundreds.n * 100 + thousands.n * 1000 + ten_thousands.n * 10000 + 1
FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) tens
CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) hundreds
CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) thousands
CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) ten_thousands;

INSERT IGNORE INTO economic_terms (id, name, normalized_name, definition, status, created_at, updated_at)
SELECT n, CONCAT('perf-term-', LPAD(n, 5, '0')), CONCAT('perf-term-', LPAD(n, 5, '0')),
       CONCAT('performance fixture term ', n), IF(MOD(n, 10) = 0, 'INACTIVE', 'ACTIVE'),
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM perf_numbers WHERE n <= 5000;

INSERT IGNORE INTO economic_term_aliases (economic_term_id, alias, normalized_alias)
SELECT n, CONCAT('perf-alias-a-', LPAD(n, 5, '0')), CONCAT('perf-alias-a-', LPAD(n, 5, '0'))
FROM perf_numbers WHERE n <= 5000;
INSERT IGNORE INTO economic_term_aliases (economic_term_id, alias, normalized_alias)
SELECT n, CONCAT('perf-alias-b-', LPAD(n, 5, '0')), CONCAT('perf-alias-b-', LPAD(n, 5, '0'))
FROM perf_numbers WHERE n <= 5000;

INSERT IGNORE INTO news_articles (
    id, title, summary, source_name, source_url, source_url_hash,
    published_at, collected_at, created_at, updated_at
)
SELECT n, CONCAT('performance news ', n), CONCAT('summary ', n), 'Performance Fixture',
       CONCAT('https://example.test/news/', n), UNHEX(SHA2(CONCAT('https://example.test/news/', n), 256)),
       TIMESTAMP('2026-01-01 00:00:00') + INTERVAL n SECOND,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM perf_numbers WHERE n <= 20000;

INSERT IGNORE INTO term_news_mappings (
    economic_term_id, news_article_id, match_type, confidence_score, matched_at
)
SELECT MOD(n - 1, 5000) + 1,
       MOD((MOD(n - 1, 5000) + 1) * 37 + FLOOR((n - 1) / 5000) * 499, 20000) + 1,
       'EXACT_NAME', 1.0000, UTC_TIMESTAMP(6)
FROM perf_numbers WHERE n <= 50000;

INSERT IGNORE INTO term_news_mappings (
    economic_term_id, news_article_id, match_type, confidence_score, matched_at
)
SELECT 1, n, 'EXACT_NAME', 1.0000, UTC_TIMESTAMP(6)
FROM perf_numbers WHERE n <= 5000;

ANALYZE TABLE economic_terms, economic_term_aliases, news_articles, term_news_mappings;

SELECT 'DATA_DISTRIBUTION' AS section;
SELECT (SELECT COUNT(*) FROM economic_terms) terms,
       (SELECT COUNT(*) FROM economic_terms WHERE status = 'ACTIVE') active_terms,
       (SELECT COUNT(*) FROM economic_term_aliases) aliases,
       (SELECT COUNT(*) FROM news_articles) news,
       (SELECT COUNT(*) FROM term_news_mappings) mappings;

SELECT 'INDEXES' AS section;
SELECT table_name, index_name, non_unique, GROUP_CONCAT(column_name ORDER BY seq_in_index) columns_in_order
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('economic_terms', 'economic_term_aliases', 'news_articles',
                     'term_news_mappings', 'popular_term_snapshots')
GROUP BY table_name, index_name, non_unique
ORDER BY table_name, index_name;

SELECT 'TERM_LIST_PAGE_0' AS section;
EXPLAIN FORMAT=TREE SELECT t.* FROM economic_terms t WHERE t.status = 'ACTIVE'
ORDER BY t.name, t.id LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE SELECT t.* FROM economic_terms t WHERE t.status = 'ACTIVE'
ORDER BY t.name, t.id LIMIT 20 OFFSET 0;

SELECT 'TERM_LIST_OFFSET_2500' AS section;
EXPLAIN ANALYZE SELECT t.* FROM economic_terms t WHERE t.status = 'ACTIVE'
ORDER BY t.name, t.id LIMIT 20 OFFSET 2500;
EXPLAIN ANALYZE SELECT COUNT(t.id) FROM economic_terms t WHERE t.status = 'ACTIVE';

SELECT 'TERM_SEARCH_NAME_AND_COUNT' AS section;
EXPLAIN FORMAT=TREE SELECT DISTINCT t.* FROM economic_terms t
LEFT JOIN economic_term_aliases a ON a.economic_term_id = t.id
WHERE t.status = 'ACTIVE' AND
      (t.normalized_name LIKE '%term-04999%' OR a.normalized_alias LIKE '%term-04999%')
ORDER BY t.name, t.id LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE SELECT DISTINCT t.* FROM economic_terms t
LEFT JOIN economic_term_aliases a ON a.economic_term_id = t.id
WHERE t.status = 'ACTIVE' AND
      (t.normalized_name LIKE '%term-04999%' OR a.normalized_alias LIKE '%term-04999%')
ORDER BY t.name, t.id LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE SELECT COUNT(DISTINCT t.id) FROM economic_terms t
LEFT JOIN economic_term_aliases a ON a.economic_term_id = t.id
WHERE t.status = 'ACTIVE' AND
      (t.normalized_name LIKE '%term-04999%' OR a.normalized_alias LIKE '%term-04999%');

SELECT 'TERM_SEARCH_ALIAS' AS section;
EXPLAIN ANALYZE SELECT DISTINCT t.* FROM economic_terms t
LEFT JOIN economic_term_aliases a ON a.economic_term_id = t.id
WHERE t.status = 'ACTIVE' AND
      (t.normalized_name LIKE '%alias-b-04999%' OR a.normalized_alias LIKE '%alias-b-04999%')
ORDER BY t.name, t.id LIMIT 20 OFFSET 0;

SELECT 'TERM_PAGE_ALIAS_HYDRATION' AS section;
EXPLAIN ANALYZE SELECT t.*, a.* FROM economic_terms t
LEFT JOIN economic_term_aliases a ON a.economic_term_id = t.id
WHERE t.id IN (1,2,3,4,5,6,7,8,9,11,12,13,14,15,16,17,18,19,21,22);

SELECT 'NEWS_PAGE_0_AND_OFFSET_10000' AS section;
EXPLAIN FORMAT=TREE SELECT n.* FROM news_articles n
ORDER BY n.published_at DESC, n.id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE SELECT n.* FROM news_articles n
ORDER BY n.published_at DESC, n.id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE SELECT n.* FROM news_articles n
ORDER BY n.published_at DESC, n.id DESC LIMIT 20 OFFSET 10000;
EXPLAIN ANALYZE SELECT COUNT(n.id) FROM news_articles n;

SELECT 'RELATED_NEWS_AND_COUNT' AS section;
EXPLAIN FORMAT=TREE SELECT m.*, n.* FROM term_news_mappings m
JOIN news_articles n ON n.id = m.news_article_id
WHERE m.economic_term_id = 1
ORDER BY n.published_at DESC, n.id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE SELECT m.*, n.* FROM term_news_mappings m
JOIN news_articles n ON n.id = m.news_article_id
WHERE m.economic_term_id = 1
ORDER BY n.published_at DESC, n.id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE SELECT COUNT(m.id) FROM term_news_mappings m WHERE m.economic_term_id = 1;

SELECT 'IDEMPOTENT_LOOKUPS' AS section;
EXPLAIN ANALYZE SELECT n.* FROM news_articles n
WHERE n.source_url_hash = UNHEX(SHA2('https://example.test/news/10000', 256));
EXPLAIN ANALYZE SELECT m.* FROM term_news_mappings m
WHERE m.economic_term_id = 1 AND m.news_article_id = 100;

SELECT 'ACTIVE_TERMS_WITH_ALIASES' AS section;
EXPLAIN ANALYZE SELECT t.*, a.* FROM economic_terms t
LEFT JOIN economic_term_aliases a ON a.economic_term_id = t.id
WHERE t.status = 'ACTIVE' ORDER BY t.id;

SELECT 'POPULAR_TERM_ID_BATCH' AS section;
EXPLAIN ANALYZE SELECT t.* FROM economic_terms t
WHERE t.id IN (1,2,3,4,5,6,7,8,9,11,12,13,14,15,16,17,18,19,21,22)
  AND t.status = 'ACTIVE';
