# MySQL 쿼리와 인덱스 점검

## 분석 환경과 방법

- 분석일: 2026-07-21
- MySQL: Docker Compose `mysql:8.0`, 실제 서버 `8.0.46`
- Schema: Flyway V1 적용 후 V2 적용 가능한 disposable `econpulse_query_analysis`
- 데이터: 경제용어 5,000건(4,500 ACTIVE), 별칭 10,000건, 뉴스 20,000건,
  용어-뉴스 매핑 54,990건(관련 뉴스 정렬 검사용 hot term 5,000건 포함)
- 도구: `EXPLAIN FORMAT=TREE`, `EXPLAIN ANALYZE`, Hibernate statistics

운영 설정에는 SQL logging을 추가하지 않았다. 실제 Hibernate SQL은 query-count 통합
테스트 한 건에 `SPRING_JPA_SHOW_SQL=true`를 임시로 주입해 확인했고, 분석 SQL은
`scripts/sql/query-plan-analysis.sql`에 고정했다. 시간은 환경 성능 기준이 아니라 같은
계획 안에서 읽은 행 수와 접근 방식의 참고값이다.

## 실제 Hibernate SQL

가독성을 위해 select column 목록과 Hibernate alias를 줄인 형태다. parameter는 모두
`?`이며 값이나 민감 정보를 로그에 남기지 않는다.

```sql
-- ACTIVE 용어 page와 count
select distinct t.* from economic_terms t
where t.status=? order by t.name,t.id limit ?;
select count(t.id) from economic_terms t where t.status=?;

-- 현재 page aliases 일괄 초기화
select t.*,a.* from economic_terms t
left join economic_term_aliases a on t.id=a.economic_term_id
where t.id in (?,?,?) order by a.id;

-- 이름/별칭 contains 검색과 count
select distinct t.* from economic_terms t
left join economic_term_aliases a on t.id=a.economic_term_id
where t.status=? and
 (t.normalized_name like concat('%',?,'%') or a.normalized_alias like concat('%',?,'%'))
order by t.name,t.id limit ?;
select count(distinct t.id) from economic_terms t
left join economic_term_aliases a on t.id=a.economic_term_id
where t.status=? and
 (t.normalized_name like concat('%',?,'%') or a.normalized_alias like concat('%',?,'%'));

-- 뉴스 page
select n.* from news_articles n order by n.published_at desc,n.id desc limit ?,?;
select count(n.id) from news_articles n;

-- 관련 뉴스 page와 count
select m.*,n.* from term_news_mappings m
join news_articles n on n.id=m.news_article_id
where m.economic_term_id=? order by n.published_at desc,n.id desc limit ?,?;
select count(m.id) from term_news_mappings m where m.economic_term_id=?;

-- 멱등 조회와 인기 용어 ID 일괄 조회
select n.* from news_articles n where n.source_url_hash=?;
select m.* from term_news_mappings m
where m.economic_term_id=? and m.news_article_id=?;
select t.* from economic_terms t where t.id in (?,?,...) and t.status=?;
```

## 실행계획 요약

| 흐름 | 실제 access/index | 실제 rows·시간 | filesort/temporary |
| --- | --- | --- | --- |
| ACTIVE 목록 page 0 | `idx_economic_terms_status_name_id`, index lookup | 20 rows, 약 0.20ms | 없음 |
| ACTIVE 목록 offset 2,500 | 같은 index, 2,520 rows scan | 약 1.39ms | 없음 |
| ACTIVE count | 같은 covering index, 4,500 rows | 약 0.78ms | 없음 |
| 이름 contains 검색 | status index + alias FK index nested loop | 4,500 terms/9,000 joined rows, 약 14.4ms | DISTINCT temporary scan |
| 검색 count | 같은 join, 9,000 rows | 약 11.2ms | `count(distinct)` |
| 별칭 contains 검색 | 같은 join | 9,000 joined rows, 약 11.4ms | DISTINCT temporary scan |
| page aliases hydrate | term PK range + alias FK index | 20 terms/40 joined rows, 약 0.05ms | 없음 |
| 뉴스 page 0 | `idx_news_articles_published_at` reverse scan | 20 rows, 약 0.04ms | 없음 |
| 뉴스 offset 10,000 | optimizer가 table scan 20,000 + top-N sort 선택 | 약 14.5ms | filesort |
| 뉴스 count | InnoDB row count scan | 약 1.47ms | 없음 |
| 관련 뉴스 hot term | mapping UNIQUE prefix + news PK lookup | 5,000 mappings, 약 9.98ms | news 정렬 top-N sort |
| 관련 뉴스 count | mapping UNIQUE covering lookup | 5,000 rows, 약 0.67ms | 없음 |
| URL hash 조회 | `uk_news_articles_source_url_hash` unique lookup | 1 row | 없음 |
| mapping pair 조회 | `uk_term_news_mappings_term_article` unique lookup | 1 row | 없음 |
| ACTIVE terms + aliases | status index + alias FK index | 4,500 terms/9,000 rows, 약 11ms | term ID 정렬 |
| popular ID batch | PRIMARY range, 20 IDs | 20 rows, 약 0.02ms | 없음 |

ACTIVE 전체+aliases는 모든 ACTIVE row를 반환하는 작업이므로 status 선택도가 낮을 때 인덱스
이득이 제한적이다. 그래도 한 EntityGraph query이며 alias당 추가 query는 없다. ID batch는
최대 100개 primary-key range lookup이고 별도 복합 인덱스가 필요하지 않다.

## 인덱스 목록과 결정

| Table | 유지 인덱스 | 지원 흐름 |
| --- | --- | --- |
| `economic_terms` | PK `id`; UNIQUE `normalized_name`; `(status,name,id)` | ID/중복 확인; ACTIVE 목록 정렬/count |
| `economic_term_aliases` | PK; UNIQUE `normalized_alias`; `(economic_term_id)` | 별칭 유일성; term join/FK |
| `news_articles` | PK; UNIQUE `source_url_hash`; `(published_at)` | 상세/조인; 멱등 저장; 최신순 reverse scan |
| `term_news_mappings` | PK; UNIQUE `(economic_term_id,news_article_id)`; `(news_article_id)` | pair 멱등성·term 조회/count; article FK |
| `popular_term_snapshots` | PK; 두 snapshot UNIQUE; FK 자동 index | 미사용 snapshot 제약만 유지 |

V1에는 UNIQUE와 같은 컬럼 순서인 일반 인덱스 두 개가 있었다.

- `idx_economic_term_aliases_normalized_alias`는
  `uk_economic_term_aliases_normalized_alias`와 동일했다.
- `idx_term_news_mappings_term_article`는
  `uk_term_news_mappings_term_article`와 동일했다.

V2는 두 일반 인덱스만 제거한다. UNIQUE는 같은 B-tree lookup과 leftmost-prefix join을
계속 지원한다. 읽기 계획은 유지하면서 alias와 mapping insert/update의 B-tree 유지 비용과
저장 공간을 줄인다. 기존 UNIQUE business constraint는 변경하지 않는다.

추가하지 않은 후보:

- `(published_at DESC,id DESC)`: 현재 단일 secondary index에 InnoDB PK가 포함되고 page 0은
  reverse scan으로 정렬을 해결한다. 큰 offset에서 optimizer가 full scan/filesort를 택하는
  문제는 같은 키를 명시한 중복 복합 인덱스로 해결되지 않는다.
- `(economic_term_id,news_article_id)` 일반 index: 동일 UNIQUE가 이미 정확히 지원한다.
- `(status,id)`: status 단독 선택도가 낮고 `(status,name,id)` prefix 또는 PK batch lookup으로
  현재 쿼리를 처리한다. ACTIVE 전체 작업의 반환량도 줄이지 못한다.
- `normalized_name`/`normalized_alias` 일반 index 추가: UNIQUE B-tree가 이미 있고 leading
  wildcard contains 검색은 어느 B-tree도 range로 사용하지 못한다.
- snapshot index: 기능이 미사용이므로 현재 제약을 확장하지 않는다.

## 검색, 페이징, count의 한계

검색 의미는 `LIKE CONCAT('%', :query, '%')`다. 선행 wildcard 때문에 normalized column의
B-tree는 검색 범위를 줄이지 못하며, OR + alias join + DISTINCT로 ACTIVE terms와 aliases를
검사한다. 5,000/10,000 규모에서는 약 11~14ms였지만 데이터에 비례해 증가한다. 이번
작업에서는 의미를 prefix로 바꾸거나 FULLTEXT/검색엔진을 도입하지 않았다. 규모 증가 시
prefix 계약, MySQL FULLTEXT/n-gram, 전용 검색엔진은 별도 제품 결정이 필요하다.

offset pagination도 유지한다. ACTIVE 목록 offset 2,500은 2,520 index rows를 읽었고 뉴스
offset 10,000은 20,000 rows scan과 filesort를 선택했다. 큰 offset 비용은 선형으로 증가한다.
수십만 관련 뉴스 또는 최신순 스크롤 UX가 필요해지면 cursor pagination을 backlog로 검토한다.

Page count는 목록에서 status covering index를 사용한다. 검색은 `count(distinct term.id)`로
alias 중복을 제거하며 실제 결과 수가 정확하다. 관련 뉴스는 mapping UNIQUE prefix만 읽는
covering count이고 fetch join과 ORDER BY가 count SQL에 포함되지 않는다.

## N+1과 쿼리 수

Hibernate statistics 통합 테스트의 결과는 다음과 같다.

- 용어 목록/검색: page select + count + 현재 page alias hydration = 각각 3 queries 고정.
- ACTIVE 전체+aliases: EntityGraph join 1 query.
- 관련 뉴스: ACTIVE 확인 + mapping/news fetch join page + count = 3 queries 고정.
- 인기 용어 ID batch: primary-key `IN` 1 query.

기존 목록/검색은 DTO 변환 시 term 수만큼 alias lazy query가 발생할 수 있었다. pagination
collection fetch join의 메모리 페이징을 피하기 위해 page query 후 현재 page ID만
EntityGraph로 일괄 초기화한다. 결과 수가 늘어도 쿼리 수는 증가하지 않는다.

## 재현과 backlog

분석은 disposable local database에 V1/V2를 적용한 뒤 다음처럼 명시적으로 실행한다.

```bash
ANALYZE_QUERY_PLANS_CONFIRM=local \
MYSQL_DATABASE=econpulse_query_analysis \
./scripts/analyze-query-plans.sh
```

스크립트는 database 이름에 `analysis`가 없으면 거부하지만 운영 DB에서 실행해서는 안 된다.
대표 데이터를 insert하고 SELECT를 실제 실행하는 `EXPLAIN ANALYZE`가 포함된다.

Backlog는 대규모 contains 검색 대안, 큰 offset cursor 계약 검토, hot term 관련 뉴스 정렬이
수십만 건으로 성장할 때의 별도 비정규화/조회 설계, 실제 운영 분포의 주기적 plan 재검토다.
