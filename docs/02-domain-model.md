# Domain Model

## 1. 패키지 구조

기본 패키지는 `com.econpulse`이며 기능 단위로 구성한다.

```text
com.econpulse
├── term        # EconomicTerm CRUD, 검색
├── news        # NewsArticle 수집, 조회
├── mapping     # 용어-뉴스 자동 매핑
├── popular     # Redis 순위, 스냅샷
└── global      # 예외, 설정, 공통 응답
```

각 기능 패키지는 필요에 따라 `api`, `application`, `domain`, `infrastructure` 하위 패키지를 둔다. 계층 간 호출은 API → application → domain/infrastructure 방향을 따른다.

## 2. 핵심 엔티티

### EconomicTerm

- 속성: `id`, `name`, `definition`, `aliases`, `createdAt`, `updatedAt`
- 규칙: 정규화된 이름은 유일하며 이름과 정의는 비어 있을 수 없다.
- 책임: 검색어 일치 여부와 공개 가능한 용어 정보를 제공한다.

### NewsArticle

- 속성: `id`, `title`, `summary`, `sourceName`, `sourceUrl`, `publishedAt`, `collectedAt`
- 규칙: `sourceUrl`은 유일하다. 원문 저작권을 고려해 초기 버전은 메타데이터와 요약만 저장한다.
- 책임: 외부 뉴스의 식별 정보와 발행 시점을 보존한다.

### TermNewsMapping

- 속성: `id`, `economicTermId`, `newsArticleId`, `matchType`, `confidenceScore`, `matchedAt`
- 규칙: 용어와 뉴스의 조합은 유일하다. 점수는 `0.0000`부터 `1.0000` 사이다.
- `matchType`: `EXACT_NAME`, `ALIAS`
- 책임: 자동 매핑 결과와 근거를 추적한다.

### PopularTermSnapshot

- 속성: `id`, `economicTermId`, `rank`, `score`, `snapshotAt`
- 규칙: 동일 스냅샷 시점에 순위와 용어가 각각 중복될 수 없다.
- 책임: Redis 실시간 순위의 시점별 감사·분석 기록을 보존한다.

## 3. 관계

- `EconomicTerm` 1:N `TermNewsMapping`
- `NewsArticle` 1:N `TermNewsMapping`
- `EconomicTerm` 1:N `PopularTermSnapshot`

JPA 연관관계는 필요한 방향만 정의하고 컬렉션의 무제한 즉시 로딩을 금지한다. API 조회는 전용 쿼리 또는 DTO 프로젝션으로 N+1 문제를 방지한다.

## 4. 핵심 서비스

- `EconomicTermService`: 검색, 상세 조회, 검색 이벤트 기록
- `NewsIngestionService`: 외부 뉴스 수집과 URL 기반 upsert
- `TermNewsMappingService`: 정규화된 이름·별칭 일치와 멱등 저장
- `PopularTermService`: Redis 점수 증가와 상위 순위 조회
- `PopularTermSnapshotService`: Redis 순위를 MySQL에 주기적으로 저장

## 5. 매핑 규칙

뉴스 제목과 요약을 유니코드 정규화하고 대소문자 및 연속 공백을 정리한 뒤 용어명과 별칭을 탐색한다. 정확한 용어명 일치는 별칭보다 높은 점수를 부여한다. 구체적인 점수 정책은 설정 가능해야 하며 변경 시 기존 결과를 재처리할 수 있어야 한다.
