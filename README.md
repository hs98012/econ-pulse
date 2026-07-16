# EconPulse

경제용어 사전에 최신 뉴스를 자동 매핑하고 Redis 기반 실시간 인기 검색어를
제공하는 Spring Boot 백엔드입니다.

현재 구현 범위는 Phase 3 진행 중입니다. Phase 2 경제용어 사전 API는 완료됐고,
Phase 3에서 뉴스 제공자 Port, 테스트용 Fake Adapter, `NewsIngestionService`의
멱등 MySQL 저장 흐름과 `NewsQueryService`의 저장 뉴스 목록·상세 조회가
구현됐으며 공개 저장 뉴스 목록·상세 API와 조건부 내부 동기화 API도 제공합니다.
조건부 `NaverNewsProvider` Adapter, 명시적 `TermNewsMapping` Application 저장 기능과
제목·요약에서 한 용어의 이름·별칭 후보를 계산하는 순수 `TermNewsMatcher`와 저장된
뉴스 ID를 활성 용어 전체와 비교해 기존 멱등 저장 경계로 연결하는 제한된
`TermNewsAutoMappingService`, 조건부 내부 rebuild와 용어별 관련 뉴스 공개 조회까지
구현됐습니다. 뉴스 수집 후 자동 호출, 전체 무제한 재처리,
스케줄러, Redis 인기 검색어 기능은 아직 구현하지 않습니다.
지정된 뉴스 ID의 재처리는 기본 비활성 내부 API로 명시적으로 실행할 수 있습니다.

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
를 직접 생성해서 사용합니다. `local` profile에서만 fixture를 가진 Fake Adapter가
Spring Bean으로 등록되며 운영 기본 profile에서는 실제 Provider처럼 자동 사용되지
않습니다. 테스트마다 새 인스턴스에 데이터를 주입해 상태 오염을 피합니다.

Fake Adapter는 제목 또는 요약에 검색어가 포함된 뉴스를 반환하고, 검색 비교 전에
trim, Unicode NFKC, 연속 공백 정리, 소문자 변환을 적용합니다. 정렬은 최신순과
관련도순을 지원하며 page/size 페이징을 적용합니다. 외부 제공자 응답에 있을 수
있는 `<b>`, `&quot;`, `&amp;` 같은 표현은 Adapter 경계에서 일반 문자열로 정리해
Port 바깥 계층이 제공자별 HTML 형식을 알지 않게 합니다.

`NaverNewsProvider`의 요청/응답 DTO와 `RestClient`는 infrastructure의 Naver 패키지에
가두고 애플리케이션 계층에는 Port 모델만 반환합니다. 내부 page/size는 Naver의
`start=page*size+1`, `display=size`로 변환하며 start 1000 초과 요청은 전송 전에
거부합니다. `RECENCY`는 `date`, `RELEVANCE`는 `sim`으로 변환합니다.

HTTP 경계는
`docs/06-news-provider-adapter-contract.md`에 고정했습니다. 재사용 가능한 추상 계약
테스트와 test-only `RestClient` reference Adapter가 OkHttp `MockWebServer`의 localhost
응답을 사용해 요청·응답 매핑, HTML 정제, 401/403/429/5xx, timeout, 연결 실패,
malformed JSON과 비밀값 비노출을 검증합니다. 같은 계약을 Naver Adapter에도 적용하며
자동 테스트는 실제 인터넷을 사용하지 않습니다.

Naver Adapter는 기본적으로 선택되지 않습니다. 실제 실행 시에만 다음 값을 외부에서
주입합니다. 자격 증명이 비어 있으면 Naver 선택 상태의 애플리케이션 기동은 안전하게
실패하며 값 자체는 오류에 노출되지 않습니다.

```bash
ECONPULSE_NEWS_PROVIDER_TYPE=naver \
NAVER_NEWS_CLIENT_ID=replace-locally \
NAVER_NEWS_CLIENT_SECRET=replace-locally \
./gradlew bootRun
```

기본 endpoint는 `https://openapi.naver.com`, connect/read timeout은 각각 2초/3초이며
환경변수로 변경할 수 있습니다. 로컬 기본 profile은 Fake Provider를 선택하므로 Naver를
사용하려면 provider type을 명시적으로 덮어써야 합니다.

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
`ErrorCode.NEWS_NOT_FOUND`로 표현합니다. `GET /api/v1/news`와
`GET /api/v1/news/{newsId}`에서 이 조회 계약을 공개합니다.

## Phase 3 용어-뉴스 매핑 저장

`TermNewsMappingService`는 명시적으로 전달된 economic term ID, news article ID,
match type과 confidence score를 하나의 트랜잭션에서 저장합니다. 조합의 unique 기준은
`(economic_term_id, news_article_id)`입니다. 신규 조합은 `CREATED`, 완전히 같거나
약한 근거는 `SKIPPED`, 더 강한 근거는 `UPDATED`입니다.

`EXACT_NAME`은 confidence score와 관계없이 `ALIAS`보다 우선합니다. 같은 match type은
점수가 높아질 때만 갱신합니다. 점수는 0.0000~1.0000, 소수점 최대 4자리만 허용하고
저장 전 scale 4로 정규화합니다. 신규 생성과 실제 갱신만 `matchedAt`을 현재 UTC로
변경하며 SKIPPED는 기존 시각을 유지합니다. 비활성 용어는 신규·갱신 모두 거부합니다.

동시 insert unique 충돌은 현재 명시적 `TERM_NEWS_MAPPING_CONFLICT`로 실패시킵니다.
순차 재실행은 멱등적이지만 자동 재시도나 잠금은 아직 제공하지 않습니다. 이 저장
서비스 자체는 이름·별칭 탐지나 조회 API를 담당하지 않으며, 뒤 절의 순수 매처와 제한된
자동 매핑 Application 흐름이 각각 후보 계산과 저장 연결을 담당합니다.

## Phase 3 순수 용어-뉴스 매칭

`TermNewsMatcher`는 JPA 엔티티 대신 불변 입력인 `TermMatchTarget`과
`NewsMatchContent`를 받아 한 용어와 한 뉴스의 최종 `TermMatchCandidate`를
계산합니다. Spring Bean, Repository, HTTP Provider와 무관한 일반 Java 객체이며
제목과 요약만 검사합니다.

우선순위는 `제목 EXACT_NAME > 요약 EXACT_NAME > 제목 ALIAS > 요약 ALIAS`이며,
동일 분류에서는 긴 정규화 표현, 그다음 사전순으로 결정합니다. 점수는 순서대로
`1.0000`, `0.9000`, `0.8000`, `0.7000`입니다. 공통 정규화는 Unicode NFKC,
trim, 연속 공백 축약, 영문 소문자화를 적용합니다. 순수 ASCII 영문·숫자 표현은
ASCII 영숫자 토큰 경계를 확인하고, 한글 및 혼합 표현은 조사 결합을 위해 부분 문자열을
허용합니다. 한 코드 포인트 별칭은 자동 후보에서 제외합니다.

상세 정책은 `docs/07-term-news-matching-policy.md`에 있습니다. 순수 매처 자체는 후보
계산만 제공하며 DB 조회나 `TermNewsMappingService` 호출 책임을 갖지 않습니다.

## Phase 3 저장 데이터 자동 매핑

단일 뉴스 처리는 `mapNews(AutoMapNewsCommand)`로 실행합니다. 뉴스 한 건을 조회한 뒤
`@EntityGraph`로 별칭을 함께 적재한 ACTIVE 용어 전체를 ID 순서대로 순차 평가하고,
후보만 기존 `TermNewsMappingService`에 전달합니다. 결과는 평가 용어, 후보,
`CREATED`/`UPDATED`/`SKIPPED`, 미일치 수를 반환하며 한 호출의 조회와 모든 저장은
하나의 트랜잭션입니다. 저장 중 실패하면 결과를 반환하지 않고 앞선 저장도 롤백합니다.

이 단일 뉴스 기능은 `NewsIngestionService`에서 자동 호출하지 않습니다. 외부 수집 성공과
매핑 실패를 결합하지 않도록 수집과 매핑은 독립 Application 기능으로 유지합니다.

단일 뉴스 내부 API는 기본 비활성입니다. local에서도 명시적으로 활성화합니다.

```bash
SPRING_PROFILES_ACTIVE=local \
ECONPULSE_INTERNAL_TERM_NEWS_MAPPING_ENABLED=true \
./gradlew bootRun
```

저장 뉴스 ID 한 건을 동기 처리합니다.

```bash
curl -i \
  -X POST \
  "http://localhost:8080/internal/api/v1/news/1/term-mappings/auto"
```

같은 요청을 반복하면 기존 후보는 `skipped`로 집계되고 행 수는 증가하지 않습니다. 이
기능 토글은 인증이 아니므로 운영에서는 인증 또는 네트워크 접근 제한이 필요합니다.
활성 용어가 커져 요청 시간이 길어지면 향후 비동기 Job 전환을 별도 설계합니다.

`TermNewsAutoMappingService`는 `TermNewsAutoMappingCommand`에 명시된 저장 뉴스 ID를
한 번의 쿼리로 읽고, 별칭까지 함께 초기화한 ACTIVE 용어 전체를 조회합니다. 명령은
중복을 제거한 양수 뉴스 ID를 오름차순 불변 목록으로 보관하며 최대 100개까지 허용합니다.
요청 뉴스가 하나라도 없으면 저장 전에 `NEWS_NOT_FOUND`로 전체 호출을 실패시킵니다.

Application 계층에서 엔티티를 `TermMatchTarget`과 `NewsMatchContent`로 변환한 뒤
뉴스 ID, 용어 ID 오름차순으로 순수 매처를 실행합니다. 후보가 있을 때만
`TermNewsMappingCommand`를 만들고 기존 `TermNewsMappingService`를 호출합니다.
`matchedText`와 `matchedField`는 현재 DB에 저장하지 않습니다.

결과는 요청·처리 뉴스 수, 활성 용어 수, 평가 조합 수, 후보 수,
`CREATED`/`UPDATED`/`SKIPPED`, 미일치 조합 수를 제공합니다. 평가 조합 수는 후보와
미일치의 합이고 후보 수는 세 저장 상태의 합입니다. AutoMappingService에는 큰
트랜잭션을 두지 않으며 각 저장은 기존 MappingService의 트랜잭션을 사용합니다. 오류는
즉시 전파하고 부분 성공 결과를 반환하지 않지만, 오류 전에 완료된 개별 트랜잭션을
일괄 롤백하는 정책은 아닙니다.

현재 계산량은 `요청 뉴스 수 × ACTIVE 용어 수`입니다. 초기 규모에서는 전체 활성 용어를
순회하되 뉴스 입력을 100건으로 제한합니다. 데이터가 증가하면 후보 용어 사전 필터링이나
검색 인덱스가 필요하며 전체 재처리는 반드시 페이지·청크 단위로 설계해야 합니다.

### 내부 매핑 재처리 API

`POST /internal/api/v1/mappings/rebuild`는 기본적으로 비활성화되며 뉴스 수집 API와
독립적으로 동작합니다. local profile에서도 명시적으로 활성화해야 합니다.

```bash
SPRING_PROFILES_ACTIVE=local \
ECONPULSE_INTERNAL_MAPPING_REBUILD_ENABLED=true \
./gradlew bootRun
```

먼저 용어 seed와 내부 뉴스 동기화 API로 저장 데이터와 뉴스 ID를 준비한 뒤 다음 요청을
실행합니다.

```bash
curl -i \
  -X POST "http://localhost:8080/internal/api/v1/mappings/rebuild" \
  -H "Content-Type: application/json" \
  -d '{"newsArticleIds":[1,2]}'
```

같은 요청을 다시 실행하면 첫 응답의 `created`가 두 번째에는 0이 되고 기존 후보는
`skipped`로 집계되며 `term_news_mappings` 행 수는 유지됩니다. 요청 배열은 최대 100개고
중복 ID는 허용하지만 Application Command에서 제거되므로 `requestedNewsCount`는 고유
ID 수입니다. 일부 ID가 없으면 `404 NEWS_NOT_FOUND`로 실패하며 부분 성공 응답은 없습니다.

이 토글은 기능 활성화 설정일 뿐 인증이 아닙니다. 운영에서는 별도 인증 또는 네트워크
접근 제한이 필요합니다. ID 없는 전체 재처리, `all`, 기간 조건은 지원하지 않습니다.

### 용어별 관련 뉴스 조회

자동 매핑 후 실제 용어 ID로 관련 뉴스를 조회합니다.

```bash
curl -i \
  "http://localhost:8080/api/v1/terms/1/news?page=0&size=20"
```

응답은 발행시각 내림차순, 같은 시각이면 뉴스 ID 내림차순이며 `matchType`과 JSON 숫자
`confidenceScore`, UTC `matchedAt`을 포함합니다. 매핑이 없으면 404가 아니라 빈 페이지를
반환합니다. Application Query는 ACTIVE 용어만 허용하고 미존재·INACTIVE 용어는 기존
공개 용어 정책과 같이 `TERM_NOT_FOUND`로 처리합니다.
local 검증 순서는 용어 seed 확인 → 내부 뉴스 동기화 → 저장 뉴스 ID 확인 → 내부 매핑
rebuild → 관련 뉴스 조회입니다. 예시의 용어·뉴스 ID는 현재 로컬 DB의 실제 ID로 바꿔야
합니다. 공개 용어 상세와 동일하게 미존재 또는 INACTIVE 용어는 `404 TERM_NOT_FOUND`입니다.

## Phase 3 내부 뉴스 동기화

`POST /internal/api/v1/news/sync`는 `NewsIngestionService`를 동기 실행하고
`fetched`, `created`, `updated`, `skipped` 건수를 반환합니다. URL 해시 기반 기존
멱등 저장을 그대로 사용하므로 같은 요청을 반복해도 뉴스 행이 중복되지 않습니다.

내부 API는 기본 profile에서 비활성화됩니다. `local` profile은 local fixture를 가진
`FakeNewsProvider`를 등록하고 내부 API를 활성화합니다. Fake Provider는 운영 기본
profile에서 Bean으로 등록되지 않습니다.

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

local에서도 명시적인 환경변수로 활성화 여부를 덮어쓸 수 있습니다.

```bash
SPRING_PROFILES_ACTIVE=local \
ECONPULSE_INTERNAL_NEWS_SYNC_ENABLED=true \
./gradlew bootRun
```

동기화 요청:

```bash
curl -i \
  -X POST "http://localhost:8080/internal/api/v1/news/sync" \
  -H "Content-Type: application/json" \
  -d '{"query":"기준금리","page":0,"size":20,"sort":"RECENCY"}'
```

같은 curl을 두 번 실행한 뒤 두 번째 응답의 `created=0`, `skipped` 증가와 공개
목록 API의 `totalElements`가 유지되는지 확인합니다.

```bash
curl "http://localhost:8080/api/v1/news?page=0&size=20"
```

현재 제어는 기능 활성화 설정일 뿐 인증이 아닙니다. 운영 환경에 내부 API를 열 때는
별도 인증 또는 네트워크 접근 제한을 반드시 추가해야 합니다.

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

저장 뉴스 최신순 목록과 상세 조회:

```bash
curl "http://localhost:8080/api/v1/news?page=0&size=20"
curl "http://localhost:8080/api/v1/news/1"
```

목록은 `publishedAt DESC, id DESC` 순서이며 page 기본값은 0, size 기본값은
20입니다. page는 0 이상, size는 1~100이어야 합니다.

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
