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

상태: 진행 중. 뉴스 제공자 Port, 테스트용 Fake Adapter, NewsIngestionService의 멱등 저장까지 완료했다.

### 작업

- 완료: Spring/HTTP/provider DTO에 의존하지 않는 `NewsProvider` Port 정의
- 완료: `NewsSearchQuery`, `NewsSort`, `NewsProviderArticle`, `NewsSearchResult` 내부 모델 정의
- 완료: provider 오류를 표현하는 `NewsProviderException`, `NewsProviderErrorType` 정의
- 완료: 테스트와 로컬 개발용 in-memory `FakeNewsProvider` 구현
- 완료: `NewsIngestionService`, `NewsIngestionCommand`, `NewsIngestionResult` 구현
- 완료: 정규화 URL SHA-256 해시 기반 `NewsArticle` 멱등 저장과 갱신
- 완료: Fake Provider → 수집 서비스 → MySQL 저장 통합 테스트
- 남음: 실제 외부 뉴스 Adapter 구현
- 남음: 수집된 뉴스 조회 API 구현
- 남음: `TermNewsMapping` 저장 및 조회 구현
- 남음: 이름·별칭 기반 매핑과 내부 재처리 API 구현
- 남음: 수집 스케줄러와 운영용 동기화 흐름 구현

### 성공 기준

- 일부 충족: 같은 뉴스를 반복 수집해도 `NewsArticle` 중복 행이 생기지 않는다. `TermNewsMapping` 멱등성은 아직 남아 있다.
- 진행 중: 관련 뉴스가 발행일 최신순으로 조회된다.
- 일부 충족: 외부 제공자 Port/Fake Adapter의 검색, 정렬, 페이징, 예외 모델과 수집 저장 테스트가 통과한다.

이번 단계에서는 실제 네이버 뉴스 API, 외부 HTTP 호출, 자동 매핑, 스케줄러,
Controller, 내부 동기화 API를 구현하지 않는다. 다음 작은 작업은 저장된 뉴스를
발행일 최신순으로 조회하는 application query와 repository query를 추가하는 것이다.

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
