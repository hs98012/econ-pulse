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

- 속성: `id`, `name`, `normalizedName`, `definition`, `status`, `aliases`, `createdAt`, `updatedAt`
- 규칙: 정규화된 이름은 유일하며 이름과 정의는 비어 있을 수 없다. 삭제는 물리 삭제가 아니라 `INACTIVE` 상태 전환이다.
- 책임: 검색어 일치 여부와 공개 가능한 용어 정보를 제공한다.

### EconomicTermAlias

- 속성: `id`, `economicTermId`, `alias`, `normalizedAlias`
- 규칙: 별칭은 저장 전에 trim, Unicode NFKC, 연속 공백 정리, 소문자 변환을 적용한다. 같은 용어 내부의 정규화 별칭 중복과 정규화 결과가 용어명과 같은 별칭은 제거한다. Phase 2에서는 정규화 별칭을 전체 용어에서 유일하게 유지한다.
- 책임: 별칭 검색을 DB 쿼리와 인덱스로 처리할 수 있게 한다.

### NewsArticle

- 속성: `id`, `title`, `summary`, `sourceName`, `sourceUrl`, `publishedAt`, `collectedAt`
- 규칙: 긴 URL 인덱스 한도를 피하기 위해 `sourceUrlHash`가 유일하다. 원문 URL은 보존한다. 원문 저작권을 고려해 초기 버전은 메타데이터와 요약만 저장한다.
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
- `EconomicTerm` 1:N `EconomicTermAlias`
- `NewsArticle` 1:N `TermNewsMapping`
- `EconomicTerm` 1:N `PopularTermSnapshot`

JPA 연관관계는 필요한 방향만 정의하고 컬렉션의 무제한 즉시 로딩을 금지한다. API 조회는 전용 쿼리 또는 DTO 프로젝션으로 N+1 문제를 방지한다.

## 4. 핵심 서비스

- `EconomicTermService`: 검색, 상세 조회, 검색 이벤트 기록
- `NewsProvider`: 외부 뉴스 검색을 provider-neutral 요청·응답 모델로 감싸는 application Port
- `FakeNewsProvider`: 외부 HTTP 없이 테스트와 로컬 개발에서 사용하는 in-memory Adapter
- `NewsIngestionService`: 외부 뉴스 수집과 URL 기반 upsert. 아직 구현하지 않았다.
- `TermNewsMappingService`: 정규화된 이름·별칭 일치와 멱등 저장
- `PopularTermService`: Redis 점수 증가와 상위 순위 조회
- `PopularTermSnapshotService`: Redis 순위를 MySQL에 주기적으로 저장

## 5. 뉴스 제공자 Port 규칙

- Port 위치: `com.econpulse.news.application.port`
- Adapter 위치: `com.econpulse.news.infrastructure.provider`
- Port 요청 모델은 `query`, `page`, `size`, `sort`를 사용하며 외부 제공자의 `start`, `display` 같은 용어를 노출하지 않는다.
- Port 응답 모델은 provider article id, title, summary, source name, source URL, published at, page, size, optional total elements, has next를 표현한다.
- 외부 제공자가 전체 결과 수를 제공하지 않으면 `OptionalLong.empty()`로 표현한다.
- provider별 DTO와 HTTP client 타입은 Adapter 내부에만 둔다.
- 외부 제공자의 HTML 강조 태그나 entity는 Adapter 경계에서 일반 문자열로 정리한 뒤 Port 모델로 반환한다.
- provider 예외는 `NewsProviderException`과 `NewsProviderErrorType`으로 표현하며, 인증 실패·요청 제한·타임아웃·잘못된 응답·일시 장애와 재시도 가능 여부를 구분한다.

## 6. 매핑 규칙

뉴스 제목과 요약을 유니코드 정규화하고 대소문자 및 연속 공백을 정리한 뒤 용어명과 별칭을 탐색한다. 정확한 용어명 일치는 별칭보다 높은 점수를 부여한다. 구체적인 점수 정책은 설정 가능해야 하며 변경 시 기존 결과를 재처리할 수 있어야 한다.
