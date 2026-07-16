# API Specification

## 1. 공통 규칙

- Base path: `/api/v1`
- 요청·응답: `application/json`
- 시간: UTC 기반 ISO 8601
- 페이징 기본값: `page=0`, `size=20`; 최대 `size=100`
- 검증 오류와 도메인 오류는 공통 오류 형식을 사용한다.

```json
{
  "code": "TERM_NOT_FOUND",
  "message": "Economic term was not found.",
  "timestamp": "2026-06-16T00:00:00Z"
}
```

## 2. API 목록

### Phase 2 구현 완료 API

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/api/v1/terms` | 용어 등록 |
| `GET` | `/api/v1/terms?query=&page=&size=` | 페이징 기반 전체 조회 및 이름·별칭 검색 |
| `GET` | `/api/v1/terms/{termId}` | 용어 상세 조회 |
| `PUT` | `/api/v1/terms/{termId}` | 용어 수정 |
| `DELETE` | `/api/v1/terms/{termId}` | 용어 비활성화 삭제 |

### Phase 3 구현 완료 API

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/v1/news?page=&size=` | 수집된 뉴스 최신순 조회 |
| `GET` | `/api/v1/news/{newsId}` | 뉴스 상세 메타데이터 조회 |
| `GET` | `/api/v1/terms/{termId}/news?page=&size=` | ACTIVE 용어의 관련 뉴스 최신순 조회 |

Phase 3 핵심 흐름은 Fake Provider 수집부터 자동 매핑과 위 관련 뉴스 조회까지 실제 MySQL
E2E로 검증했으며 동일 입력 재실행 시 뉴스·매핑·응답 content가 중복되지 않는다.

### Phase 3 구현 완료 내부 API

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/internal/api/v1/news/sync` | 조건부 활성화 뉴스 수집 작업 실행 |
| `POST` | `/internal/api/v1/mappings/rebuild` | 조건부 활성화 지정 뉴스 자동 매핑 재처리 |
| `POST` | `/internal/api/v1/news/{newsId}/term-mappings/auto` | 조건부 활성화 단일 뉴스 자동 매핑 |

### 이후 Phase 예정 API

아래 API는 이후 Phase에서 구현한다.

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/v1/popular-terms?limit=10` | 실시간 인기 용어 조회 |

내부 API는 외부 공개 대상이 아니며 운영 환경에서 별도 인증 또는 네트워크 제한을 적용한다.

## 3. 주요 응답 계약

### 용어 등록·수정 요청

`POST /api/v1/terms`

요청:

```json
{
  "name": "기준금리",
  "definition": "중앙은행이 금융시장에 적용하는 기준이 되는 금리",
  "aliases": ["정책금리"]
}
```

용어명과 정의는 비어 있을 수 없고, 용어명과 별칭은 100자 이하여야 한다.
별칭은 저장 전에 trim, Unicode NFKC 정규화, 연속 공백 정리, 소문자 변환을
적용한다. 같은 용어 내부의 중복 별칭과 정규화 결과가 용어명과 같은 별칭은
저장하지 않는다.

응답: `201 Created`

`Location: /api/v1/terms/1`

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

### 용어 목록·검색

`GET /api/v1/terms?query=&page=&size=`

- `query`가 없거나 공백이면 ACTIVE 용어 전체를 반환한다.
- `query`가 있으면 정규화된 용어명과 정규화된 별칭을 DB 쿼리에서 검색한다.
- 기본값은 `page=0`, `size=20`이고 `size` 최대값은 100이다.
- `page < 0`, `size < 1`, `size > 100`, 숫자 타입 오류는 `400 INVALID_REQUEST`를 반환한다.
- 정렬은 용어명 오름차순, 동률이면 ID 오름차순이다.
- 같은 용어가 이름과 별칭에 동시에 매칭되어도 한 번만 반환한다.

```json
{
  "content": [
    {
      "id": 1,
      "name": "기준금리",
      "definition": "중앙은행이 금융시장에 적용하는 기준이 되는 금리",
      "aliases": ["정책금리"]
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

예: 이름 검색

`GET /api/v1/terms?query=기준&page=0&size=20`

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

예: 별칭 검색

`GET /api/v1/terms?query=base&page=0&size=20`

응답 구조는 이름 검색과 동일하다. 같은 용어가 이름과 별칭에 동시에 일치해도
`content`에는 한 번만 포함된다.

### 용어 상세

`GET /api/v1/terms/1`

```json
{
  "id": 1,
  "name": "기준금리",
  "definition": "중앙은행이 금융시장에 적용하는 기준이 되는 금리",
  "aliases": ["정책금리"],
  "latestNewsCount": 12,
  "createdAt": "2026-06-16T00:00:00Z",
  "updatedAt": "2026-06-16T00:00:00Z"
}
```

존재하지 않는 용어는 `404 TERM_NOT_FOUND`를 반환한다. Redis 인기 점수 증가는 향후 인기 검색어 단계에서 연결한다.

### 용어 수정

`PUT /api/v1/terms/1`

요청:

```json
{
  "name": "기준금리",
  "definition": "중앙은행의 정책금리",
  "aliases": ["정책금리", "base rate"]
}
```

응답: `200 OK`

```json
{
  "id": 1,
  "name": "기준금리",
  "definition": "중앙은행의 정책금리",
  "aliases": ["정책금리", "base rate"],
  "latestNewsCount": 0,
  "createdAt": "2026-07-14T00:00:00Z",
  "updatedAt": "2026-07-14T00:01:00Z"
}
```

### 삭제 정책

`DELETE /api/v1/terms/{termId}`는 행을 물리 삭제하지 않고 `INACTIVE`로 변경한다.
목록, 검색, 상세 조회는 `ACTIVE` 용어만 노출한다. 이미 `INACTIVE`인 용어에 대한
DELETE는 멱등적으로 `204 No Content`를 반환한다. 같은 이름 또는 별칭을 가진
`INACTIVE` 용어가 있어도 재등록은 허용하지 않는다.

응답: `204 No Content`

### 오류 예제

Validation 실패: `400 INVALID_REQUEST`

```json
{
  "code": "INVALID_REQUEST",
  "message": "name: must not be blank",
  "timestamp": "2026-07-14T00:00:00Z"
}
```

존재하지 않는 용어: `404 TERM_NOT_FOUND`

```json
{
  "code": "TERM_NOT_FOUND",
  "message": "Economic term was not found.",
  "timestamp": "2026-07-14T00:00:00Z"
}
```

중복 이름: `409 DUPLICATE_TERM_NAME`

```json
{
  "code": "DUPLICATE_TERM_NAME",
  "message": "Economic term name already exists.",
  "timestamp": "2026-07-14T00:00:00Z"
}
```

### 관련 뉴스 항목

```json
{
  "id": 10,
  "title": "한국은행 기준금리 동결",
  "summary": "정책 결정 배경을 설명한다.",
  "sourceName": "Example News",
  "sourceUrl": "https://example.com/news/10",
  "publishedAt": "2026-06-15T03:00:00Z",
  "matchType": "EXACT_NAME",
  "confidenceScore": 1.0
}
```

### 저장 뉴스 목록

`GET /api/v1/news?page=0&size=20`

- page 기본값은 0이며 0 이상이어야 한다.
- size 기본값은 20이며 1~100이어야 한다.
- 정렬은 `publishedAt DESC`, 발행 시각이 같으면 `id DESC`이며 클라이언트 sort는 지원하지 않는다.
- 정렬과 페이징은 Repository 쿼리에서 수행한다.
- 숫자 타입 오류와 범위 오류는 `400 INVALID_REQUEST`를 반환한다.

응답: `200 OK`

```json
{
  "content": [
    {
      "id": 10,
      "title": "한국은행 기준금리 동결",
      "summary": "통화정책 결정 배경을 설명한다.",
      "sourceName": "Example News",
      "sourceUrl": "https://example.com/news/10",
      "publishedAt": "2026-07-14T02:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

빈 목록도 `200 OK`를 반환한다.

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

잘못된 page 또는 size: `400 INVALID_REQUEST`

```json
{
  "code": "INVALID_REQUEST",
  "message": "Invalid request.",
  "timestamp": "2026-07-14T02:00:00Z"
}
```

### 저장 뉴스 상세

`GET /api/v1/news/10`

`newsId`는 양수여야 한다. 0, 음수, 숫자가 아닌 값은 `400 INVALID_REQUEST`를
반환하며 조회 서비스로 전달하지 않는다.

응답: `200 OK`

```json
{
  "id": 10,
  "title": "한국은행 기준금리 동결",
  "summary": "통화정책 결정 배경을 설명한다.",
  "sourceName": "Example News",
  "sourceUrl": "https://example.com/news/10",
  "publishedAt": "2026-07-14T02:00:00Z",
  "collectedAt": "2026-07-14T02:05:00Z",
  "createdAt": "2026-07-14T02:05:00Z",
  "updatedAt": "2026-07-14T02:05:00Z"
}
```

모든 시간은 UTC ISO 8601 형식이다. 목록과 상세 모두 `sourceUrlHash`와
`termMappings`를 노출하지 않으며 목록에는 수집·생성·수정 시각도 노출하지 않는다.

존재하지 않는 뉴스: `404 NEWS_NOT_FOUND`

```json
{
  "code": "NEWS_NOT_FOUND",
  "message": "News article was not found.",
  "timestamp": "2026-07-14T02:00:00Z"
}
```

### 인기 용어 항목

```json
{
  "rank": 1,
  "termId": 1,
  "name": "기준금리",
  "score": 42.0
}
```

`limit`은 1~100이어야 하며 잘못된 값은 `400 INVALID_REQUEST`를 반환한다.

## 4. 내부 뉴스 동기화

`POST /internal/api/v1/news/sync`

이 API는 외부 공개 대상이 아닌 운영자·내부 작업용 동기 실행 API다. 기본 설정
`econpulse.internal.news-sync.enabled=false`에서는 Controller와 수집 서비스 Bean이
등록되지 않는다. `local` profile은 local fixture용 `FakeNewsProvider`와 API를
활성화하며 환경변수 `ECONPULSE_INTERNAL_NEWS_SYNC_ENABLED`로 명시적으로 덮어쓸 수
있다. 운영 환경에서는 실제 Provider와 함께 별도 인증 또는 네트워크 제한이 필요하다.

요청:

```json
{
  "query": "기준금리",
  "page": 0,
  "size": 20,
  "sort": "RECENCY"
}
```

- query는 null, 빈 문자열, 공백 문자열일 수 없다.
- page는 0 이상, size는 1~100이다.
- sort는 `RECENCY` 또는 `RELEVANCE`만 허용한다.
- 알 수 없는 필드, 잘못된 enum, 잘못된 JSON은 `400 INVALID_REQUEST`다.
- API DTO는 Controller 경계에서 `NewsIngestionCommand`로 변환된다.

응답: `200 OK`

```json
{
  "fetched": 3,
  "created": 2,
  "updated": 1,
  "skipped": 0
}
```

동일 요청을 반복하면 정규화 URL SHA-256 해시 기준으로 기존 행을 재사용한다. 변경이
없는 기사는 `skipped`로 집계하며 뉴스 행 수는 증가하지 않는다. 외부 Provider 호출이
실패하면 저장을 시작하지 않는다.

Provider 오류 정책:

| Provider 오류 | HTTP | code |
|---|---:|---|
| 요청 제한, 시간 초과, 일시 장애 | 503 | `NEWS_PROVIDER_UNAVAILABLE` |
| 인증 실패, 잘못된 Provider 응답 | 502 | `NEWS_PROVIDER_BAD_RESPONSE` |

응답에는 API 키, Provider 원문 응답, 외부 라이브러리 예외명, stack trace, 재시도
가능 여부를 노출하지 않는다.

```json
{
  "code": "NEWS_PROVIDER_UNAVAILABLE",
  "message": "News provider is temporarily unavailable.",
  "timestamp": "2026-07-14T02:00:00Z"
}
```

초기 버전은 요청이 끝날 때까지 기다리는 동기 `200 OK` 방식이다. 작업 시간이
길어지면 별도 Job 리소스와 `202 Accepted` 방식으로 확장한다.

## 5. 단일 뉴스 내부 자동 매핑

`POST /internal/api/v1/news/{newsId}/term-mappings/auto`

특정 저장 뉴스 한 건을 현재 ACTIVE 경제용어 전체와 동기 비교하는 내부 API다. 요청
body는 사용하지 않는다. 기본 설정
`econpulse.internal.term-news-mapping.enabled=false`에서는 Controller Bean과 경로가
등록되지 않으며 요청은 404다. local에서도 환경변수
`ECONPULSE_INTERNAL_TERM_NEWS_MAPPING_ENABLED=true`로 명시적으로 활성화해야 한다.
뉴스 동기화·다중 ID rebuild 토글과 독립적이고 뉴스 수집 후 자동 호출하지 않는다.

- `newsId`는 1 이상의 정수다. 0, 음수, 문자열은 `400 INVALID_REQUEST`다.
- 뉴스가 없으면 `404 NEWS_NOT_FOUND`다.
- ACTIVE 용어만 별칭과 함께 조회하며 INACTIVE 용어는 평가하지 않는다.
- 한 번에 뉴스 한 건만 처리하며 전체 뉴스·날짜 범위 처리는 지원하지 않는다.
- Controller는 `AutoMapNewsCommand`를 만들고 Application 결과를 응답 DTO로 변환한다.

응답: `200 OK`

```json
{
  "newsArticleId": 15,
  "evaluatedTerms": 120,
  "matchedCandidates": 4,
  "created": 3,
  "updated": 0,
  "skipped": 1,
  "noMatch": 116
}
```

`evaluatedTerms = matchedCandidates + noMatch`이며
`matchedCandidates = created + updated + skipped`다. 같은 상태에서 재실행하면 기존
후보는 `skipped`가 되고 매핑 행 수는 증가하지 않는다. 조회와 모든 후보 저장은 하나의
트랜잭션이며 저장 실패 시 전체 작업을 롤백하고 결과를 반환하지 않는다.

초기 데이터 규모에서는 요청이 끝날 때까지 기다리는 동기 `200 OK` 방식이다. 활성 용어
증가로 처리 시간이 길어지면 비동기 Job 전환을 별도 설계한다. 현재 `202 Accepted`, Job,
전체 재처리와 스케줄러는 지원하지 않는다. 기능 토글은 인증을 대신하지 않으므로 운영에서는
인증 또는 네트워크 접근 제한이 필요하다.

## 6. 내부 매핑 재처리

`POST /internal/api/v1/mappings/rebuild`

기본 설정 `econpulse.internal.mapping-rebuild.enabled=false`에서는 Controller Bean이
등록되지 않는다. local profile에서도 환경변수
`ECONPULSE_INTERNAL_MAPPING_REBUILD_ENABLED=true`로 명시적으로 활성화해야 한다.
뉴스 동기화 토글과 독립적이며 뉴스 수집 완료 후 자동 호출하지 않는다. 이 설정은 인증을
대신하지 않으므로 운영에서는 인증 또는 네트워크 접근 제한이 필요하다.

요청:

```json
{
  "newsArticleIds": [10, 11, 12]
}
```

- 배열은 null 또는 빈 값일 수 없고 각 ID는 null이 아닌 양수여야 한다.
- 원본 요청 배열은 최대 100개다. 101개 이상은 `400 INVALID_REQUEST`다.
- 중복 ID는 허용하며 Application Command가 중복을 제거하고 ID 오름차순으로 처리한다.
- `requestedNewsCount`는 중복 제거 후 실제 요청 대상이 된 고유 뉴스 수다.
- 알 수 없는 필드, 문자열 ID, 타입 불일치와 잘못된 JSON은 `400 INVALID_REQUEST`다.
- 모든 요청 뉴스가 존재해야 한다. 하나라도 없으면 매핑 전에 전체 요청을
  `404 NEWS_NOT_FOUND`로 실패시키며 누락 ID 목록이나 내부 정보를 노출하지 않는다.
- `{}`, `all`, 기간 조건과 ID 없는 전체 뉴스 재처리는 지원하지 않는다.

응답: `200 OK`

```json
{
  "requestedNewsCount": 3,
  "processedNewsCount": 3,
  "activeTermCount": 20,
  "evaluatedPairCount": 60,
  "matchedCandidateCount": 5,
  "created": 3,
  "updated": 1,
  "skipped": 1,
  "unmatchedPairCount": 55
}
```

| 필드 | 의미 |
|---|---|
| `requestedNewsCount` | 중복 제거 후 요청된 뉴스 수 |
| `processedNewsCount` | 존재 검증 후 실제 처리한 뉴스 수 |
| `activeTermCount` | 각 뉴스와 비교한 ACTIVE 용어 수 |
| `evaluatedPairCount` | 매처를 실행한 뉴스·용어 조합 수 |
| `matchedCandidateCount` | 후보가 존재한 조합 수 |
| `created` | 신규 매핑 저장 수 |
| `updated` | 더 강한 근거로 갱신한 매핑 수 |
| `skipped` | 기존 동일·강한 근거를 유지한 수 |
| `unmatchedPairCount` | 후보가 없는 조합 수 |

`evaluatedPairCount = matchedCandidateCount + unmatchedPairCount`이며
`matchedCandidateCount = created + updated + skipped`다. 입력 및 Repository 순서와
무관하게 뉴스 ID, 용어 ID 오름차순으로 실행한다. 같은 요청을 반복하면 기존 행을
재사용해 `created=0`, `skipped` 증가가 예상되며 매핑 행 수는 증가하지 않는다.

API는 동기 `200 OK` 방식이고 부분 성공 응답을 제공하지 않는다. 예상하지 못한 실행 오류는
전체 호출을 실패시키지만 AutoMappingService의 개별 저장 트랜잭션 정책상 오류 전에
커밋된 저장을 호출 전체 단위로 되돌리는 계약은 아니다. 비동기 Job, 전체 재처리,
스케줄러는 아직 지원하지 않는다.

## 7. 용어별 관련 뉴스

`GET /api/v1/terms/{termId}/news?page=0&size=20`

- `termId`는 양수이며 공개 용어 상세와 동일하게 ACTIVE 용어만 조회한다.
- 미존재 또는 INACTIVE 용어는 `404 TERM_NOT_FOUND`다.
- page 기본값은 0이고 0 이상, size 기본값은 20이고 1~100이다.
- 클라이언트 정렬 입력은 지원하지 않는다.
- DB 정렬은 `NewsArticle.publishedAt DESC`, 동일 시각은 `NewsArticle.id DESC`다.
- 매핑이 없으면 `200 OK`와 빈 페이지를 반환한다.

응답:

```json
{
  "content": [
    {
      "id": 31,
      "title": "한국은행 기준금리 동결",
      "summary": "통화정책 결정 배경을 설명한다.",
      "sourceName": "example.com",
      "sourceUrl": "https://example.com/news/31",
      "publishedAt": "2026-07-15T02:00:00Z",
      "matchType": "EXACT_NAME",
      "confidenceScore": 1.0000,
      "matchedAt": "2026-07-15T03:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

`matchType`은 용어명 일치 `EXACT_NAME` 또는 별칭 일치 `ALIAS`다.
`confidenceScore`는 DB `DECIMAL(5,4)`를 `BigDecimal` JSON 숫자로 반환한다. JSON
표현에서 scale 4를 유지한다. `matchedAt`은 저장된 최종 매핑 시각을 UTC ISO 8601로
반환한다. 매핑 ID, URL hash, 엔티티 연관관계와 내부 생성·수정 시각은 노출하지 않는다.

빈 결과:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```
