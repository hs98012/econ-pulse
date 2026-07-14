# EconPulse

경제용어 사전에 최신 뉴스를 자동 매핑하고 Redis 기반 실시간 인기 검색어를
제공하는 Spring Boot 백엔드입니다.

현재 구현 범위는 Phase 3 진행 중입니다. Phase 2 경제용어 사전 API는 완료됐고,
Phase 3에서 뉴스 제공자 Port, 테스트용 Fake Adapter, `NewsIngestionService`의
멱등 MySQL 저장 흐름과 `NewsQueryService`의 저장 뉴스 목록·상세 조회가
구현됐습니다. 뉴스 Controller와 공개 뉴스 API, 실제 외부 뉴스 API 연동, 뉴스
자동 매핑, 스케줄러, 내부 동기화 API, Redis 인기 검색어 기능은 아직 구현하지
않습니다.

## 기술 스택

- Java 17
- Spring Boot 3.5.15
- Gradle Wrapper
- Spring Web, Spring Data JPA, Validation
- Flyway
- MySQL 8.0, Redis 7
- Lombok
- Docker Compose
- Testcontainers, ArchUnit, JaCoCo, Checkstyle

## Phase 3 뉴스 제공자 Port

뉴스 제공자 연동은 `com.econpulse.news.application.port.NewsProvider` Port 뒤에
숨깁니다. Port 모델은 `NewsSearchQuery`, `NewsSort`, `NewsProviderArticle`,
`NewsSearchResult`이며 Spring, HTTP 클라이언트, 특정 외부 제공자 DTO에 의존하지
않습니다.

테스트와 로컬 개발에는 `com.econpulse.news.infrastructure.provider.FakeNewsProvider`
를 직접 생성해서 사용합니다. Fake Adapter는 Spring Bean으로 등록하지 않으므로
운영 환경에서 실제 Provider처럼 자동 사용되지 않습니다. 테스트마다 새 인스턴스에
데이터를 주입해 상태 오염을 피합니다.

Fake Adapter는 제목 또는 요약에 검색어가 포함된 뉴스를 반환하고, 검색 비교 전에
trim, Unicode NFKC, 연속 공백 정리, 소문자 변환을 적용합니다. 정렬은 최신순과
관련도순을 지원하며 page/size 페이징을 적용합니다. 외부 제공자 응답에 있을 수
있는 `<b>`, `&quot;`, `&amp;` 같은 표현은 Adapter 경계에서 일반 문자열로 정리해
Port 바깥 계층이 제공자별 HTML 형식을 알지 않게 합니다.

실제 외부 Adapter를 추가할 때 provider별 요청/응답 DTO는 adapter 내부 패키지에만
두고, 애플리케이션 계층에는 Port 모델만 반환해야 합니다.

`NewsIngestionService`는 `NewsProvider`를 호출해 받은 기사를 `news_articles`에
저장합니다. 중복 기준은 정규화된 `sourceUrl`의 SHA-256 해시(`source_url_hash`)
입니다. URL은 앞뒤 공백 제거, URI 문법 검증, scheme/host 소문자화, fragment 제거,
기본 포트 제거를 적용하고 query parameter는 임의로 제거하지 않습니다.

수집 결과는 `fetched`, `created`, `updated`, `skipped`로 집계합니다. 신규 해시는
생성, 기존 해시는 제목·요약·출처·URL·발행시각 중 실제 변경이 있으면 갱신,
완전히 같은 기사나 동일 응답 내 중복 URL은 건너뜀으로 계산합니다. 기존 정상
요약은 외부 응답의 빈 요약으로 덮어쓰지 않습니다. Provider 오류가 발생하면 저장
작업을 수행하지 않고, DB unique 충돌은 수집 예외로 변환해 트랜잭션을 실패시킵니다.

## Phase 3 저장 뉴스 조회

`NewsQueryService`는 수집된 `NewsArticle`을 JPA 엔티티 대신 목록용
`NewsSummaryResponse`와 상세용 `NewsDetailResponse`로 반환합니다. 목록은
Repository 쿼리에서 `publishedAt DESC, id DESC`로 정렬하고 page/size를 적용하며,
page는 0 이상, size는 1~100입니다. 향후 Controller 기본값으로 사용할 수 있도록
`NewsPageQuery.defaults()`는 page 0, size 20을 제공합니다.

DB의 UTC `DATETIME(6)`에 매핑된 `LocalDateTime`은 공통 변환기를 거쳐 DTO에서
`Instant`로 노출합니다. 존재하지 않는 뉴스는 `NewsNotFoundException`과
`ErrorCode.NEWS_NOT_FOUND`로 표현합니다. 이 기능은 application 계층까지만
구현됐으며 `/api/v1/news` Controller와 공개 엔드포인트는 아직 없습니다.

## 로컬 실행

필수 도구:

- JDK 17
- Docker 및 Docker Compose

환경변수 기본값은 로컬 개발용으로 설정되어 있습니다. 값을 변경하려면 예제
파일을 기준으로 `.env`를 작성합니다.

```bash
cp .env.example .env
```

MySQL과 Redis를 실행합니다.

```bash
docker compose up -d
docker compose ps
```

로컬 MySQL과 다른 Docker MySQL이 기본 포트 `3306` 또는 인접 포트를 사용 중일
수 있으므로 Docker MySQL은 충돌을 피하기 위해 호스트 포트 `3308`로 실행합니다.
컨테이너 내부 포트는 `3306`입니다.

두 서비스가 `healthy` 상태가 된 뒤 애플리케이션을 실행합니다.

```bash
./gradlew bootRun
```

현재 개발 단계는 Flyway 마이그레이션으로 스키마를 생성하고
`spring.jpa.hibernate.ddl-auto=validate`로 엔티티와 DB 계약을 검증합니다.

한글 데이터가 `??`로 깨져 저장되는 경우 기존 MySQL 볼륨이 utf8mb4 설정 전에
생성되었을 수 있습니다. 로컬 개발 데이터 삭제가 가능하다면 DB 볼륨을 초기화한
뒤 다시 실행합니다.

```bash
docker compose down -v
docker compose up -d
```

애플리케이션은 기본적으로 `http://localhost:8080`에서 실행됩니다.
경제용어 API 기본 경로는 `/api/v1/terms`입니다.

### API 예제

경제용어 등록:

```bash
curl -X POST http://localhost:8080/api/v1/terms \
  -H 'Content-Type: application/json' \
  -d '{"name":"기준금리","definition":"중앙은행이 금융시장에 적용하는 기준이 되는 금리","aliases":["정책금리","base rate"]}'
```

응답은 `201 Created`와 `Location: /api/v1/terms/{id}`를 반환합니다.

```json
{
  "id": 1,
  "name": "기준금리",
  "definition": "중앙은행이 금융시장에 적용하는 기준이 되는 금리",
  "aliases": ["정책금리", "base rate"],
  "latestNewsCount": 0,
  "createdAt": "2026-07-14T00:00:00Z",
  "updatedAt": "2026-07-14T00:00:00Z"
}
```

페이징 목록 조회:

```bash
curl 'http://localhost:8080/api/v1/terms?page=0&size=20'
```

이름 검색:

```bash
curl 'http://localhost:8080/api/v1/terms?query=기준&page=0&size=20'
```

별칭 검색:

```bash
curl 'http://localhost:8080/api/v1/terms?query=정책&page=0&size=20'
```

목록과 검색 응답:

```json
{
  "content": [
    {
      "id": 1,
      "name": "기준금리",
      "definition": "중앙은행이 금융시장에 적용하는 기준이 되는 금리",
      "aliases": ["정책금리", "base rate"]
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

상세 조회:

```bash
curl 'http://localhost:8080/api/v1/terms/1'
```

수정:

```bash
curl -X PUT http://localhost:8080/api/v1/terms/1 \
  -H 'Content-Type: application/json' \
  -d '{"name":"기준금리","definition":"중앙은행의 정책금리","aliases":["정책금리","base rate"]}'
```

비활성화 삭제:

```bash
curl -X DELETE http://localhost:8080/api/v1/terms/1
```

Validation 실패:

```json
{
  "code": "INVALID_REQUEST",
  "message": "name: must not be blank",
  "timestamp": "2026-07-14T00:00:00Z"
}
```

존재하지 않는 용어:

```json
{
  "code": "TERM_NOT_FOUND",
  "message": "Economic term was not found.",
  "timestamp": "2026-07-14T00:00:00Z"
}
```

중복 이름:

```json
{
  "code": "DUPLICATE_TERM_NAME",
  "message": "Economic term name already exists.",
  "timestamp": "2026-07-14T00:00:00Z"
}
```

한 번에 인프라와 애플리케이션을 실행하려면 다음 스크립트를 사용할 수 있습니다.

```bash
./scripts/run-local.sh
```

## 검증

```bash
bash -n scripts/*.sh
./scripts/check.sh
docker compose config
```

`./scripts/check.sh`는 스크립트 문법 검사, `./gradlew clean check`,
`docker compose config`를 중복 없이 실행합니다. Docker가 실행 중이면
Testcontainers 기반 MySQL 통합 테스트도 함께 실행됩니다.

로컬 데이터베이스와 Redis 볼륨을 초기화하려면 다음 명령을 실행합니다.

```bash
./scripts/reset-db.sh
```

## 로컬 Seed 데이터

Flyway 기본 마이그레이션에는 운영용 샘플 데이터를 넣지 않습니다. 테스트 데이터는
테스트 코드에서 만들고, 로컬 개발용 경제용어 샘플은 명시적으로 실행하는
`local` profile seed만 사용합니다. 운영 환경에서 seed는 자동 실행되지 않으며,
실제 API 키나 외부 뉴스 데이터도 포함하지 않습니다.

로컬 MySQL이 실행 중일 때 다음 명령으로 샘플 경제용어 10개를 넣습니다.

```bash
./scripts/seed-local.sh
```

포함 용어는 기준금리, 환율, 물가상승률, 국내총생산, 소비자물가지수, 양적완화,
채권, 주가수익비율, 경기침체, 무역수지입니다. 각 용어에는 이름 검색과 별칭
검색 검증이 가능한 별칭이 포함됩니다. 스크립트는 같은 정규화 이름이 이미 있으면
건너뛰므로 재실행해도 중복 데이터를 만들지 않습니다.

## 검색 성능 검토

현재 Phase 2에서는 추측으로 인덱스를 추가하지 않았습니다. 로컬 MySQL에 성능
확인용 경제용어 5,000개와 별칭 10,000개를 생성하고 실제 검색 쿼리의
`EXPLAIN ANALYZE`를 보려면 다음 명령을 실행합니다.

```bash
./scripts/explain-term-search.sh
```

스크립트는 ACTIVE 필터, 정규화 이름 검색, 정규화 별칭 검색, `DISTINCT`,
페이징 정렬, full table scan 여부를 확인할 수 있는 실행 계획을 출력합니다.
실행 계획이 명확한 병목과 개선 근거를 보여줄 때만 별도 Flyway 마이그레이션으로
인덱스를 추가합니다.
