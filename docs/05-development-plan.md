# Development Plan

## 진행 원칙

각 Phase는 이전 Phase의 성공 기준을 충족한 뒤 시작한다. 구현 중 API, 도메인, DB 계약이 바뀌면 관련 문서를 같은 변경에 포함한다.

## Phase 0. 하네스와 계약

### 작업

- 요구사항, 도메인, API, DB 문서 확정
- 검증·로컬 실행·DB 초기화 스크립트 준비
- Git 제외 규칙 설정

### 성공 기준

- 요구된 문서와 스크립트가 모두 존재한다.
- `bash -n scripts/*.sh`와 `./scripts/check.sh`가 빈 Gradle 저장소에서도 성공한다.
- 애플리케이션 코드는 생성하지 않는다.

## Phase 1. 프로젝트 및 인프라 기반

### 작업

- Java 17 Spring Boot Gradle 프로젝트 생성
- Spring Web, JPA, Validation, Lombok 의존성 구성
- Docker Compose에 MySQL 8.0과 Redis 7 정의
- 환경별 설정과 공통 오류 응답 구현

### 성공 기준

- `docker compose up -d`로 의존 서비스가 healthy 상태가 된다.
- `./gradlew test`와 `./gradlew build`가 성공한다.
- 애플리케이션이 로컬에서 기동하고 DB·Redis 연결 검증이 통과한다.

## Phase 2. 경제용어 사전

상태: 완료.

### 작업

- 완료: `EconomicTerm` 엔티티와 `EconomicTermAlias` 정규화 테이블 구현
- 완료: Flyway 초기 마이그레이션과 `ddl-auto=validate` 전환
- 완료: `/api/v1/terms` CRUD, 페이징 전체 조회·검색, 검증, 공통 오류 응답
- 완료: soft delete를 위한 `TermStatus`와 ACTIVE 전용 조회
- 완료: 서비스, repository, controller, ArchUnit 테스트와 Testcontainers MySQL 인프라
- 완료: API 계약 예제, seed/test data 정책, 검색 성능 검토 스크립트와 문서화

### 성공 기준

- 충족: 이름과 별칭 검색, 페이징, 상세 조회 계약 테스트가 통과한다.
- 충족: 중복 정규화 이름이 DB에서 차단된다.
- 충족: 중복 정규화 별칭이 DB에서 차단된다.
- 충족: API가 JPA 엔티티를 직접 노출하지 않는다.
- 충족: Flyway 마이그레이션과 MySQL 기반 통합 테스트가 통과한다.
- 충족: 문서와 구현의 Phase 2 API 계약이 일치한다.
- 충족: `./scripts/check.sh`와 `docker compose config`가 성공한다.

Phase 2 완료 후 다음 작업은 Phase 3이다. Phase 3의 첫 작업은 뉴스 제공자 포트와 Fake Adapter 구현이다.

## Phase 3. 뉴스 수집과 자동 매핑

상태: 완료.

### 작업

- 완료: Spring/HTTP/provider DTO에 의존하지 않는 `NewsProvider` Port 정의
- 완료: `NewsSearchQuery`, `NewsSort`, `NewsProviderArticle`, `NewsSearchResult` 내부 모델 정의
- 완료: provider 오류를 표현하는 `NewsProviderException`, `NewsProviderErrorType` 정의
- 완료: 테스트와 로컬 개발용 in-memory `FakeNewsProvider` 구현
- 완료: `NewsIngestionService`, `NewsIngestionCommand`, `NewsIngestionResult` 구현
- 완료: 정규화 URL SHA-256 해시 기반 `NewsArticle` 멱등 저장과 갱신
- 완료: Fake Provider → 수집 서비스 → MySQL 저장 통합 테스트
- 완료: `NewsPageQuery` 검증과 공통 `PageResponse` 기반 저장 뉴스 목록 조회
- 완료: Repository의 `publishedAt DESC, id DESC` 최신순 DB 페이징
- 완료: 목록·상세 DTO, UTC `Instant` 변환, `NEWS_NOT_FOUND` 예외 처리
- 완료: 수집 서비스 → MySQL 저장 → 조회 서비스 통합 테스트
- 완료: `GET /api/v1/news`, `GET /api/v1/news/{newsId}` 공개 조회 API와 계약 테스트
- 완료: 기본 비활성화 `POST /internal/api/v1/news/sync` 동기 수집 API
- 완료: local 전용 Fake Provider fixture와 Provider 오류의 502/503 공통 오류 변환
- 완료: 내부 API → Fake Provider → 멱등 MySQL 저장 통합 테스트
- 완료: MockWebServer 기반 재사용 HTTP Adapter 계약 테스트와 외부 JSON fixture
- 완료: 요청·응답·HTML·HTTP 오류·timeout·연결 실패·비밀값 비노출 계약 문서화
- 완료: 공통 HTTP 계약을 만족하는 조건부 `NaverNewsProvider`, 전용 DTO·매퍼·timeout·오류 변환 구현
- 완료: MockWebServer → Naver Adapter → 수집 서비스 → MySQL 멱등 저장 통합 테스트
- 완료: `TermNewsMappingCommand`, 결과 모델과 CREATED/UPDATED/SKIPPED 멱등 저장
- 완료: EXACT_NAME > ALIAS 및 같은 유형의 score 향상만 허용하는 도메인 갱신 정책
- 완료: 비활성 용어 거부, UTC matchedAt, Repository와 MySQL 통합 테스트
- 완료: 엔티티 없이 `TermMatchTarget`과 `NewsMatchContent`를 받는 순수 `TermNewsMatcher`
- 완료: EXACT_NAME/ALIAS, 제목/요약, 긴 표현/사전순의 결정적 후보 선택과 고정 confidence score
- 완료: 공통 NFKC·공백·소문자 정규화, ASCII 영숫자 토큰 경계와 한 코드 포인트 별칭 제외 정책
- 완료: 최대 100개 고유 뉴스 ID 명령 검증과 저장 뉴스 일괄 조회
- 완료: 별칭을 함께 초기화하는 ACTIVE 용어 전체 조회와 뉴스 ID → 용어 ID 안정 순서
- 완료: 엔티티를 순수 입력으로 변환하고 기존 멱등 저장 서비스를 호출하는 `TermNewsAutoMappingService`
- 완료: 뉴스 한 건을 ACTIVE 용어 전체와 비교하고 모든 저장을 한 트랜잭션으로 묶는 `mapNews` Application 경계
- 완료: `@EntityGraph` 기반 ACTIVE 용어·별칭 일괄 조회와 단일 뉴스 결과 집계·롤백 검증
- 완료: 기본 비활성 단일 뉴스 내부 자동 매핑 API, 독립 기능 토글과 반복 호출 멱등성 검증
- 완료: 평가·후보·CREATED/UPDATED/SKIPPED·미일치 결과 집계와 MySQL 통합 테스트
- 완료: 기본 비활성 `POST /internal/api/v1/mappings/rebuild`와 API DTO·검증·공통 오류 응답
- 완료: 최대 100개 원본 ID, 중복 제거, 안정 순서와 반복 호출 멱등성의 MockMvc/MySQL 검증
- 완료: `GET /api/v1/terms/{termId}/news`와 match type·confidence score 공개 응답
- 완료: 발행시각 DESC, 뉴스 ID DESC의 DB 페이징과 Controller·Repository·MySQL 통합 테스트
- 완료: ACTIVE 용어 관련 뉴스 Application Query의 UTC matched time, 빈/마지막/범위 밖 페이지와 자동 매핑 재실행 중복 방지 검증
- 완료: Fake Provider → 멱등 뉴스 저장 → 단일 뉴스 자동 매핑 → 공개 관련 뉴스 조회 핵심 E2E와 재실행 멱등성 검증

### 성공 기준

- 충족: 같은 뉴스와 매핑 명령을 반복해도 중복 행이 생기지 않고 관련 뉴스 content도 중복되지 않는다. 동시 신규 매핑 충돌은 명시적 예외로 실패한다.
- 충족: 저장 뉴스와 용어별 관련 뉴스는 발행일과 ID 내림차순으로 DB 페이징 조회된다.
- 충족: 외부 제공자 Port와 Fake/Naver Adapter의 검색, 정렬, 페이징, 예외 모델과 수집 저장 테스트가 통과한다. 실제 자격 증명을 사용한 운영 smoke 검증은 backlog다.
- 충족: Fake Provider부터 뉴스 수집, 자동 매핑, 공개 관련 뉴스 조회까지 실제 MySQL E2E 테스트가 통과한다.

Phase 3 핵심 제품 흐름은 완료했다. 실제 운영 Naver 자격 증명 smoke, 뉴스별 연결 용어
조회, 뉴스 수집 후 자동 매핑 orchestration, ID 없는 전체·날짜 범위 재처리, 비동기 Job,
Spring Batch와 수집 스케줄러는 Phase 3 완료를 막지 않는 운영 개선 backlog로 관리한다.
다음 구현 단계는 Phase 4 Redis 인기 검색어다.

## Phase 4. Redis 인기 검색어

### 작업

- 유효 검색 이벤트의 Sorted Set 점수 증가
- 인기 용어 API와 Redis 장애 폴백 구현
- `PopularTermSnapshot` 주기 저장 구현

### 성공 기준

- 동시 검색 증가가 유실되지 않는다.
- 순위, limit 검증, 존재하지 않는 term ID 처리 테스트가 통과한다.
- Redis 장애 중에도 용어 검색 API는 정상 응답한다.
- 동일 시점 스냅샷 중복이 방지된다.

## Phase 5. 통합 품질과 운영 준비

### 작업

- Testcontainers 또는 Compose 기반 통합 테스트
- Actuator 상태 점검, 로깅, 메트릭 구성
- 인덱스와 주요 쿼리 성능 검토
- 운영 문서 및 CI 작성

### 성공 기준

- 클린 환경에서 전체 검증이 한 번에 재현된다.
- 핵심 API, 뉴스 재수집, Redis 장애 시나리오가 자동화된다.
- 비밀값 없이 설정 예제와 운영 절차가 문서화된다.

## Codex 검증 명령어

모든 작업 후:

```bash
bash -n scripts/*.sh
./scripts/check.sh
```

Docker Compose 파일이 추가된 뒤:

```bash
docker compose config
./scripts/reset-db.sh
```

로컬 기동 검증이 필요한 변경:

```bash
./scripts/run-local.sh
```

실행하지 못한 명령은 누락하지 말고 원인과 함께 작업 결과에 기록한다.
