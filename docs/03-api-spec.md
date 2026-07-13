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

### 이후 Phase 예정 API

아래 API는 Phase 3 또는 Phase 4에서 구현한다. Phase 2 완료 시점에는 구현하지 않는다.

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/v1/terms/{termId}/news?page=&size=` | 용어 관련 최신 뉴스 조회 |
| `GET` | `/api/v1/news?page=&size=` | 수집된 뉴스 최신순 조회 |
| `GET` | `/api/v1/news/{newsId}` | 뉴스 상세 메타데이터 조회 |
| `GET` | `/api/v1/popular-terms?limit=10` | 실시간 인기 용어 조회 |
| `POST` | `/internal/api/v1/news/sync` | 뉴스 수집 작업 실행 |
| `POST` | `/internal/api/v1/mappings/rebuild` | 용어-뉴스 매핑 재처리 |

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

## 4. 내부 작업 응답

내부 작업은 중복 요청에 안전해야 한다. 초기 구현은 동기 실행 결과로 처리 건수(`collected`, `created`, `updated`, `mapped`, `skipped`)를 반환한다. 작업 시간이 길어지면 별도 Job 리소스와 `202 Accepted` 방식으로 확장한다.
