# API Specification

## 1. 공통 규칙

- Base path: `/api`
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

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/api/terms` | 용어 등록 |
| `GET` | `/api/terms` | 용어 전체 조회 |
| `GET` | `/api/terms/{termId}` | 용어 상세 조회 |
| `GET` | `/api/terms/search?keyword=` | 이름·별칭으로 용어 검색 |
| `PUT` | `/api/terms/{termId}` | 용어 수정 |
| `DELETE` | `/api/terms/{termId}` | 용어 삭제 |
| `GET` | `/api/v1/terms?query=&page=&size=` | 향후 페이징 기반 이름·별칭 검색 |
| `GET` | `/api/v1/terms/{termId}/news?page=&size=` | 용어 관련 최신 뉴스 조회 |
| `GET` | `/api/v1/news?page=&size=` | 수집된 뉴스 최신순 조회 |
| `GET` | `/api/v1/news/{newsId}` | 뉴스 상세 메타데이터 조회 |
| `GET` | `/api/v1/popular-terms?limit=10` | 실시간 인기 용어 조회 |
| `POST` | `/internal/api/v1/news/sync` | 뉴스 수집 작업 실행 |
| `POST` | `/internal/api/v1/mappings/rebuild` | 용어-뉴스 매핑 재처리 |

내부 API는 외부 공개 대상이 아니며 운영 환경에서 별도 인증 또는 네트워크 제한을 적용한다.

## 3. 주요 응답 계약

### 용어 상세

```json
{
  "id": 1,
  "name": "기준금리",
  "definition": "중앙은행이 금융시장에 적용하는 기준이 되는 금리",
  "aliases": ["정책금리"],
  "latestNewsCount": 12
}
```

존재하지 않는 용어는 `404 TERM_NOT_FOUND`를 반환한다. Redis 인기 점수 증가는 향후 인기 검색어 단계에서 연결한다.

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
