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
| `aliases` | `JSON` | NOT NULL |
| `created_at` | `DATETIME(6)` | NOT NULL |
| `updated_at` | `DATETIME(6)` | NOT NULL |

이름·별칭 검색 요구가 커지면 별칭을 별도 테이블로 정규화한다.

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

긴 URL은 SHA-256 해시로 유일성을 보장하고 원본 URL도 보존한다.

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

### `popular_term_snapshots`

| 컬럼 | 타입 | 제약 |
|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT |
| `economic_term_id` | `BIGINT` | NOT NULL, FK |
| `rank_position` | `INT` | NOT NULL |
| `score` | `DOUBLE` | NOT NULL |
| `snapshot_at` | `DATETIME(6)` | NOT NULL |

유니크 인덱스는 `(snapshot_at, economic_term_id)`와 `(snapshot_at, rank_position)`에 둔다.

삭제 정책은 뉴스와 용어를 기본적으로 물리 삭제하지 않는 방향으로 설계한다. 삭제 기능이 필요해지면 상태 컬럼과 보존 정책을 먼저 문서화한다.

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
