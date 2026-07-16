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

- 속성: `id`, `title`, `summary`, `sourceName`, `sourceUrl`, `sourceUrlHash`, `publishedAt`, `collectedAt`
- 규칙: 긴 URL 인덱스 한도를 피하기 위해 정규화된 `sourceUrl`의 SHA-256 `sourceUrlHash`가 유일하다. 원문 URL은 정규화한 형태로 보존한다. 원문 저작권을 고려해 초기 버전은 메타데이터와 요약만 저장한다.
- 책임: 외부 뉴스의 식별 정보와 발행 시점을 보존하고, 재수집 데이터로 갱신 여부를 판단한다.

### TermNewsMapping

- 속성: `id`, `economicTermId`, `newsArticleId`, `matchType`, `confidenceScore`, `matchedAt`
- 규칙: 용어와 뉴스의 조합은 유일하다. 점수는 `0.0000`부터 `1.0000` 사이다.
- `matchType`: `EXACT_NAME`, `ALIAS`
- 책임: 매핑 결과와 근거를 추적하고 더 강한 근거만 기존 상태를 갱신한다.

### TermNewsMatcher 입력과 후보

- `TermMatchTarget`: 양수 term ID, 정규화된 이름, 정규화·중복 제거된 불변 별칭 목록을 보유한다. 빈 별칭과 이름과 같은 별칭은 제거한다.
- `NewsMatchContent`: 양수 news article ID와 한 번 정규화된 제목·요약을 보유한다. 요약은 비어 있을 수 있다.
- `TermMatchCandidate`: term/article ID, `MatchType`, scale 4 confidence score, 정규화된 matched text와 `TITLE`/`SUMMARY` 필드를 보유한다.
- `TermNewsMatcher`: 한 용어와 한 뉴스 입력만 비교해 결정적인 최종 후보 하나 또는 빈 결과를 반환하는 순수 Java 정책이다. JPA 엔티티, Repository, Spring, HTTP Provider와 무관하다.

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
- `NaverNewsProvider`: `provider.type=naver`일 때만 등록되어 Naver 뉴스 검색 응답을 Port 모델로 변환하는 HTTP Adapter
- `NewsIngestionService`: `NewsProvider` 결과를 수집하고 정규화 URL 해시 기준으로 `NewsArticle`을 멱등 저장한다.
- `NewsQueryService`: 저장된 뉴스를 `publishedAt DESC, id DESC` 순서로 Repository에서 페이징 조회하고 목록·상세 DTO로 변환한다. 존재하지 않는 ID는 `NEWS_NOT_FOUND` 비즈니스 예외로 처리한다.
- `TermNewsMappingService`: 명시적으로 전달된 용어·뉴스·근거를 검증하고 멱등 저장
- `TermNewsAutoMappingService`: 최대 100개의 명시적 뉴스 ID를 ACTIVE 용어 전체와 순수 매처로 비교하고 후보만 기존 매핑 저장 서비스에 전달해 결과를 집계한다.
- `MappingRebuildController`: 기본 비활성 내부 API에서 API 요청을 자동 매핑 Command로 변환하고 집계 결과 DTO만 반환한다. AutoMappingService에만 의존하며 저장 규칙을 보유하지 않는다.
- `TermRelatedNewsQueryService`: ACTIVE 용어 존재를 확인한 뒤 매핑과 뉴스를 DB 최신순 페이징으로 조회해 공개 응답 모델로 변환한다. 매핑 생성·갱신 책임은 없다.
- `PopularTermService`: Redis 점수 증가와 상위 순위 조회
- `PopularTermSnapshotService`: 향후 Redis 순위를 MySQL에 주기적으로 저장할 예정

## 5. 뉴스 제공자 Port 규칙

- Port 위치: `com.econpulse.news.application.port`
- Adapter 위치: `com.econpulse.news.infrastructure.provider`
- Port 요청 모델은 `query`, `page`, `size`, `sort`를 사용하며 외부 제공자의 `start`, `display` 같은 용어를 노출하지 않는다.
- Port 응답 모델은 provider article id, title, summary, source name, source URL, published at, page, size, optional total elements, has next를 표현한다.
- 외부 제공자가 전체 결과 수를 제공하지 않으면 `OptionalLong.empty()`로 표현한다.
- provider별 DTO와 HTTP client 타입은 Adapter 내부에만 둔다.
- 외부 제공자의 HTML 강조 태그나 entity는 Adapter 경계에서 일반 문자열로 정리한 뒤 Port 모델로 반환한다.
- provider 예외는 `NewsProviderException`과 `NewsProviderErrorType`으로 표현하며, 인증 실패·요청 제한·타임아웃·잘못된 응답·일시 장애와 재시도 가능 여부를 구분한다.
- 실제 HTTP Adapter는 Provider별 DTO와 HTTP client 타입을 infrastructure 내부에 가두고 `docs/06-news-provider-adapter-contract.md`의 공통 계약 테스트를 통과해야 한다.
- 외부 짧은 문자열은 Adapter 경계의 `ExternalNewsTextSanitizer`에서 태그·기본 entity·연속 공백을 정리한다.
- 기본 connect timeout은 2초, read timeout은 3초이며 설정으로 변경 가능하고 무제한 timeout은 금지한다.
- Naver page는 `start=page*size+1`로 변환하며 1000 초과는 호출 전에 거부한다. `RECENCY=date`, `RELEVANCE=sim`이다.
- Naver 기사는 유효한 `originallink`를 우선하고 `link`로 fallback한다. 선택 URL의 host가 source name과 안정적인 provider article id의 근거다.
- Naver Adapter는 응답 metadata의 start/display가 요청과 모순되면 잘못된 응답으로 처리하고, 항목 수와 start 1000 제한까지 고려해 다음 페이지 여부를 계산한다.

## 6. 뉴스 수집 저장 규칙

- `NewsIngestionCommand`는 query, page, size, sort를 담고 Provider 호출 전에 유효성을 검증한다.
- Provider 호출 실패 시 DB 저장을 수행하지 않는다.
- Provider 응답이 반환되면 한 수집 요청을 하나의 트랜잭션으로 처리한다.
- `sourceUrl`은 trim, URI 문법 검증, scheme/host 소문자화, fragment 제거, 기본 포트 제거를 적용한다. query parameter는 보존한다.
- 중복 기준은 정규화 URL의 UTF-8 바이트에 대한 SHA-256 해시 32바이트다.
- 한 Provider 응답 안에 같은 해시가 반복되면 먼저 등장한 항목만 사용하고 이후 항목은 `skipped`로 집계한다.
- 기존 뉴스는 title, summary, sourceName, sourceUrl, publishedAt이 실제 변경된 경우 `updated`로 집계한다.
- 기존 뉴스가 완전히 같으면 `skipped`로 집계한다. 단, `collectedAt`은 재수집 시각으로 갱신한다.
- 외부 응답의 빈 요약은 기존 정상 요약을 덮어쓰지 않는다.
- 동시 저장 등으로 DB unique 충돌이 발생하면 수집 예외로 변환하고 트랜잭션을 실패시킨다. 복잡한 락이나 자동 재시도는 아직 구현하지 않는다.
- `POST /internal/api/v1/news/sync`는 API DTO를 `NewsIngestionCommand`로 변환해 동기 수집을 명시적으로 실행한다. Controller는 `NewsIngestionService`에만 의존한다.
- 내부 동기화 API와 수집 서비스 Bean은 `econpulse.internal.news-sync.enabled=true`일 때만 생성하며 기본값은 false다.
- Fake Provider Bean과 로컬 fixture는 `local` profile에만 존재하고 운영 기본 profile에서는 자동 선택되지 않는다.
- Naver Provider Bean은 type이 `naver`이고 client ID/secret이 모두 있을 때만 기동하며 Fake와 동시에 등록되지 않는다.
- Provider timeout, rate limit, temporary failure는 `NEWS_PROVIDER_UNAVAILABLE`, 인증 실패와 잘못된 응답은 `NEWS_PROVIDER_BAD_RESPONSE`로 변환하며 외부 상세는 노출하지 않는다.

## 7. 저장 뉴스 조회 규칙

- `NewsPageQuery`는 page 0 이상, size 1~100을 생성 시점에 검증하며 기본 조건은 page 0, size 20이다.
- 목록 정렬과 페이징은 DB Repository 쿼리에서 수행하고 `termMappings`는 함께 로딩하지 않는다.
- 목록은 `NewsSummaryResponse`, 상세는 `NewsDetailResponse`를 사용하며 JPA 엔티티와 URL 해시를 노출하지 않는다.
- DB 엔티티의 UTC `LocalDateTime`은 공통 변환기를 통해 DTO의 `Instant`로 변환한다.
- `GET /api/v1/news`와 `GET /api/v1/news/{newsId}`가 이 application 조회 기능을 공개하며 Controller는 `NewsQueryService`에만 의존한다.

## 8. 매핑 규칙

- `(economicTermId, newsArticleId)` 조합은 하나만 저장한다.
- 신규 조합은 `CREATED`, 동일하거나 약한 근거는 `SKIPPED`, 강한 근거는 `UPDATED`다.
- 우선순위는 `EXACT_NAME > ALIAS`다. ALIAS에서 EXACT_NAME은 갱신하고 반대는 건너뛴다.
- 같은 match type은 새 confidence score가 수치상 높을 때만 갱신한다. 비교에는 `BigDecimal.compareTo`를 사용한다.
- confidence score는 0.0000~1.0000이며 최대 소수점 4자리만 입력받아 scale 4로 저장한다. 추가 자릿수는 반올림하지 않고 거부한다.
- ACTIVE 용어만 생성·갱신할 수 있다. 미존재 용어, 미존재 뉴스, INACTIVE 용어는 서로 다른 예외로 처리한다.
- 생성과 실제 근거 갱신은 주입된 UTC Clock으로 `matchedAt`을 기록한다. SKIPPED는 기존 시각을 유지한다.
- 순차 재실행은 멱등적이다. 동시 신규 insert unique 충돌은 현재 명시적 application 충돌 예외로 트랜잭션을 실패시키며 자동 재시도·락은 운영 준비 단계에서 검토한다.
- 제목·요약에서 한 용어의 이름·별칭 후보를 계산하는 순수 매처는 구현했다. 후보 우선순위, 점수, 정규화와 경계 규칙은 `docs/07-term-news-matching-policy.md`를 따른다.
- 제한된 뉴스 ID 목록을 DB에서 조회하고 후보를 `TermNewsMappingService`로 연결하는 자동 매핑 Application 흐름을 구현했다.
- `mapNews`는 뉴스 한 건과 ACTIVE 용어 전체를 순차 비교하며 조회와 후보 저장 전체를 하나의 트랜잭션으로 처리한다. 저장 실패 시 일부 결과를 반환하지 않고 전체를 롤백한다.
- 기본 비활성 내부 `POST /internal/api/v1/news/{newsId}/term-mappings/auto`는 `mapNews`만 동기 호출하고 집계 DTO를 반환한다. Controller는 자동 매핑 Application Service 외 저장소·매처·엔티티를 참조하지 않는다.
- ACTIVE 용어 조회는 `@EntityGraph`로 별칭을 함께 적재해 N+1을 방지하며, 엔티티 컬렉션은 정규화 문자열의 불변 `TermMatchTarget`으로 변환한다.
- 자동 매핑은 뉴스 ID와 용어 ID 오름차순으로 처리한다. ACTIVE 용어만 별칭과 함께 조회하며 비활성 용어는 평가하지 않는다. 모든 요청 뉴스가 존재해야 하고 후보 없는 조합은 저장 서비스를 호출하지 않는다.
- 집계의 `evaluatedPairCount = matchedCandidateCount + unmatchedPairCount`, `matchedCandidateCount = created + updated + skipped` 관계를 보장한다.
- 최대 100개의 명시적 뉴스 ID를 받는 조건부 내부 매핑 재처리 Controller를 구현했다. 뉴스 동기화와 독립적으로 실행한다.
- `TermRelatedNewsQueryService`는 ACTIVE 용어의 관련 뉴스를 `publishedAt DESC, newsArticle.id DESC`로 DB 페이징한다. join fetch와 명시적 count query로 N+1 없이 뉴스 공개 필드, match type, confidence score와 UTC matched time을 반환하며 매핑이 없으면 빈 페이지다.
- 기존 `GET /api/v1/terms/{termId}/news`는 이 Application Query를 사용한다. 이번 Query 보강에서는 Controller를 새로 추가하거나 저장 정책을 변경하지 않았다.
- Fake Provider → `NewsIngestionService` → `mapNews` → 공개 관련 뉴스 API 핵심 E2E는 실제 MySQL에서 재실행 멱등성까지 검증했다.
- Phase 3는 완료했다. 뉴스 수집 후 자동 호출, 뉴스별 연결 용어 조회, ID 없는 전체 재처리와 스케줄러는 후속 운영 개선 backlog다.

## 5. 실시간 인기 용어 집계

- `PopularTermService`는 `PopularTermStore` Port와 UTC `Clock`에만 의존한다.
- 기록 명령은 양수 경제용어 ID를 현재 UTC 날짜의 Redis 일간 집계로 전달하며 매번
  MySQL 용어 존재 여부를 조회하지 않는다.
- `PopularTermScore`는 경제용어 ID, 정수 score와 1부터 시작하는 rank만 갖는다.
- Redis Adapter는 잘못된 member, null·음수·소수·안전 정수 범위 초과 score를 손상된
  저장 상태로 거부한다.
- `PopularTermSnapshot`은 향후 순위 영속 보관 책임이며 현재 실시간 흐름에서 사용하지 않는다.
- `PopularTermQueryService`는 Redis 상위 limit개의 ID를 ACTIVE 상태 조건으로 MySQL에서
  한 번에 조회한다. aliases는 조회하지 않고 이름과 정의만 Application 결과로 변환한다.
- Redis에만 있거나 INACTIVE인 ID는 제외하고 Redis 결과 순서를 유지한 채 rank를 1부터
  다시 부여한다. 추가 후보를 보충하지 않아 최종 결과는 limit보다 적을 수 있다.
