# Database Schema

## 1. MySQL 8.0

모든 테이블은 `utf8mb4`, UTC 타임스탬프, `BIGINT` 자동 증가 기본 키를 사용한다. 실제 구현에서는 Flyway 마이그레이션으로 관리한다.

### `economic_terms`

| 컬럼 | 타입 | 제약 |
|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT |
| `name` | `VARCHAR(100)` | NOT NULL |
| `normalized_name` | `VARCHAR(100)` | NOT NULL, UNIQUE |
| `definition` | `TEXT` | NOT NULL |
| `status` | `VARCHAR(20)` | NOT NULL, INDEX |
| `created_at` | `DATETIME(6)` | NOT NULL |
| `updated_at` | `DATETIME(6)` | NOT NULL |

정규화 이름은 trim, Unicode NFKC, 연속 공백 정리, 소문자 변환을 적용한다.
`ACTIVE`와 `INACTIVE` 상태를 사용하며 API 삭제는 `INACTIVE` 전환이다. 같은
정규화 이름을 가진 `INACTIVE` 용어가 있어도 재등록하지 않는다.

### `economic_term_aliases`

| 컬럼 | 타입 | 제약 |
|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT |
| `economic_term_id` | `BIGINT` | NOT NULL, FK |
| `alias` | `VARCHAR(100)` | NOT NULL |
| `normalized_alias` | `VARCHAR(100)` | NOT NULL, UNIQUE, INDEX |

별칭은 별도 테이블로 정규화한다. 같은 용어 내부 중복 별칭과 정규화 결과가
용어명과 같은 별칭은 저장하지 않는다. 가장 단순한 Phase 2 정책으로 별칭의
정규화 값은 전체 용어에서 유일해야 하며, `INACTIVE` 용어의 별칭도 재사용하지 않는다.

### `news_articles`

| 컬럼 | 타입 | 제약 |
|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT |
| `title` | `VARCHAR(500)` | NOT NULL |
| `summary` | `TEXT` | NULL |
| `source_name` | `VARCHAR(100)` | NOT NULL |
| `source_url` | `VARCHAR(1000)` | NOT NULL |
| `source_url_hash` | `BINARY(32)` | NOT NULL, UNIQUE |
| `published_at` | `DATETIME(6)` | NOT NULL, INDEX |
| `collected_at` | `DATETIME(6)` | NOT NULL |
| `created_at` | `DATETIME(6)` | NOT NULL |
| `updated_at` | `DATETIME(6)` | NOT NULL |

긴 URL은 SHA-256 해시로 유일성을 보장하고 원본 URL도 보존한다. utf8mb4 인덱스
길이 제한 때문에 원본 URL 문자열에는 직접 unique index를 두지 않는다.

`source_url` 저장 전 정규화 정책은 다음과 같다.

- 앞뒤 공백 제거
- URI 문법 검증
- scheme과 host 소문자화
- fragment 제거
- HTTP 80, HTTPS 443 기본 포트 제거
- query parameter 보존

정규화 URL의 UTF-8 바이트를 SHA-256으로 계산해 `source_url_hash BINARY(32)`에
저장한다. 같은 해시가 이미 있으면 신규 행을 만들지 않고 기존 행을 재사용한다.
기존 기사와 외부 응답의 title, summary, sourceName, sourceUrl, publishedAt이
다르면 갱신하고, 같으면 건너뜀으로 집계한다. `collected_at`은 재수집 시각으로
갱신한다. DB unique 충돌은 최종 안전장치이며 현재 정책은 수집 트랜잭션 실패다.

### `term_news_mappings`

| 컬럼 | 타입 | 제약 |
|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT |
| `economic_term_id` | `BIGINT` | NOT NULL, FK |
| `news_article_id` | `BIGINT` | NOT NULL, FK |
| `match_type` | `VARCHAR(30)` | NOT NULL |
| `confidence_score` | `DECIMAL(5,4)` | NOT NULL |
| `matched_at` | `DATETIME(6)` | NOT NULL |

유니크 인덱스는 `(economic_term_id, news_article_id)`, 뉴스 조회 인덱스는 `(economic_term_id, news_article_id)`와 `(news_article_id)`로 둔다.

Application 입력 score는 0.0000~1.0000과 소수점 최대 4자리를 검증하고 scale 4로
정규화한다. 같은 조합이 없으면 생성하고, `EXACT_NAME > ALIAS` 우선순위 또는 같은
유형의 더 높은 score인 경우에만 기존 근거와 `matched_at`을 갱신한다. 동일·약한
근거는 기존 행과 `matched_at`을 유지한다. INACTIVE 용어는 매핑할 수 없다.

unique 제약은 최종 동시성 안전장치다. 현재 순차 요청은 멱등 처리하고 동시 신규 insert
충돌은 application 충돌 예외로 트랜잭션을 실패시킨다. 같은 트랜잭션에서 rollback-only
상태를 무시하고 재조회하지 않으며, 자동 재시도나 잠금은 운영 준비 단계에서 검토한다.

### `popular_term_snapshots`

| 컬럼 | 타입 | 제약 |
|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT |
| `economic_term_id` | `BIGINT` | NOT NULL, FK |
| `rank_position` | `INT` | NOT NULL |
| `score` | `DOUBLE` | NOT NULL |
| `snapshot_at` | `DATETIME(6)` | NOT NULL |

유니크 인덱스는 `(snapshot_at, economic_term_id)`와 `(snapshot_at, rank_position)`에 둔다.

삭제 정책은 뉴스와 용어를 기본적으로 물리 삭제하지 않는 방향으로 설계한다.
Phase 2 용어 삭제는 `economic_terms.status = INACTIVE`로 처리한다.

## 2. Redis 7 사용 방식

### 실시간 인기 검색어

- 키: `econpulse:popular:terms:realtime`
- 자료구조: Sorted Set
- member: `EconomicTerm.id` 문자열
- score: 유효한 검색 또는 상세 조회 횟수
- 기록: `ZINCRBY`
- 조회: `ZREVRANGE ... WITHSCORES`

동점 정렬을 용어 ID 오름차순으로 보장하기 위해 애플리케이션에서 2차 정렬한다. 존재하지 않는 용어 ID는 결과에서 제외한다.

### 운영 정책

- 스냅샷 작업은 설정된 주기마다 상위 N개를 읽어 `popular_term_snapshots`에 저장한다.
- 스냅샷 성공 후에도 실시간 키는 유지하며, 초기 버전의 점수 초기화는 운영 설정으로 결정한다.
- Redis 장애는 검색 API 실패로 전파하지 않고 경고 및 메트릭을 남긴다.
- 테스트 키는 `econpulse:test:*` 네임스페이스를 사용하고 테스트 후 삭제한다.
- 여러 인스턴스의 동시 점수 증가는 Redis 원자 연산에 맡긴다.

## 3. Seed 및 테스트 데이터 정책

- Flyway 기본 마이그레이션에는 운영용 샘플 데이터를 넣지 않는다.
- 테스트 데이터는 테스트 코드 또는 테스트 전용 fixture에서 생성한다.
- 로컬 개발용 샘플 데이터는 `local` profile과 `econpulse.seed.enabled=true`가 명시된 경우에만 생성한다.
- `scripts/seed-local.sh`는 seed 실행 후 애플리케이션을 종료하며, 같은 정규화 이름이 이미 있으면 건너뛰어 재실행해도 중복 행을 만들지 않는다.
- 운영 환경에서 seed 기능이 자동 실행되지 않아야 하며, 실제 API 키나 외부 뉴스 데이터는 seed에 포함하지 않는다.

## 4. 검색 성능 검토

현재 검색 쿼리는 `status = ACTIVE` 조건과 `normalized_name LIKE '%query%'` 또는
`normalized_alias LIKE '%query%'` 조건을 함께 사용하고, `DISTINCT`와
`ORDER BY name ASC, id ASC`를 적용한다. leading wildcard 검색은 일반 B-tree
인덱스만으로 선택도가 크게 개선되지 않을 수 있다.

성능 검토는 `scripts/explain-term-search.sh`로 수행한다. 이 스크립트는 로컬
MySQL에 경제용어 5,000개와 별칭 10,000개 이상의 성능 확인용 데이터를
idempotent하게 생성한 뒤 다음 실행 계획을 출력한다.

- ACTIVE 목록 조회와 페이징 정렬
- 정규화 이름 검색
- 정규화 별칭 검색
- `DISTINCT`로 인한 임시 테이블 또는 filesort 여부
- 불필요한 full table scan 여부

Phase 2 종료 시점에는 추가 인덱스를 도입하지 않는다. 현재 마이그레이션에는
`economic_terms(status, name, id)`, `economic_terms(normalized_name)`,
`economic_term_aliases(normalized_alias)`, `economic_term_aliases(economic_term_id)`
인덱스가 있다. 실제 운영 규모에서 `EXPLAIN ANALYZE`가 특정 쿼리의 병목과
인덱스 개선 효과를 보여줄 때 신규 Flyway 마이그레이션으로 인덱스를 추가한다.

2026-07-14 로컬 MySQL 검토 결과:

- 성능 fixture: 경제용어 5,000개 이상, 별칭 10,000개 이상.
- ACTIVE 목록 쿼리: `idx_economic_terms_status_name_id`를 사용해 `status`, `name`, `id` 순서의 목록 페이징을 처리했다.
- 이름 검색 쿼리: `status` 인덱스로 ACTIVE 용어를 좁힌 뒤 별칭을 `economic_term_id` 인덱스로 조인했다. `LIKE '%perf-term-4999%'` 조건 때문에 `normalized_name` B-tree 인덱스는 검색 조건에 직접 활용되지 않았다.
- 별칭 검색 쿼리: 이름 검색과 같은 계획을 사용했고, `LIKE '%perf-alias-b-4999%'` 조건 때문에 `normalized_alias` B-tree 인덱스는 직접 활용되지 않았다.
- 이름/별칭 통합 검색은 `DISTINCT` 중복 제거를 위해 임시 테이블을 사용했다.
- 5,000개/10,000개 규모의 로컬 실행 시간은 약 15-17ms였고, 현재 Phase 2 기준으로 신규 인덱스의 개선 근거는 불충분하다.
- 향후 검색 규모가 커지면 prefix 검색 계약, full-text index, n-gram 보조 테이블, 또는 검색 엔진 도입을 별도 설계로 검토한다.

### 뉴스 최신순 조회 인덱스 검토

Phase 3 저장 뉴스 목록 쿼리는 `ORDER BY published_at DESC, id DESC`와 DB limit/offset
페이징을 사용한다. 현재 `idx_news_articles_published_at(published_at)` 보조 인덱스는
InnoDB에서 기본키 `id`를 함께 보유하고 MySQL 8.0은 역방향 인덱스 스캔을 지원한다.
현재 데이터 규모와 실행 계획 측정 없이 별도 `(published_at DESC, id DESC)` 인덱스가
개선된다는 근거는 부족하므로 신규 Flyway 마이그레이션을 추가하지 않는다. 운영
유사 데이터에서 `EXPLAIN ANALYZE`가 filesort 또는 페이지 지연을 보여주고 복합
인덱스 적용 전후 개선이 확인될 때 다시 추가한다.
