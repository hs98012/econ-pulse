# News Provider Adapter Contract

## 1. 상태와 목적

Phase 3의 실제 외부 NewsProvider Adapter는 아직 구현하지 않았다. 이 문서는 향후
Naver 등 HTTP Adapter가 `NewsProvider` Port를 구현할 때 지켜야 할 경계와 재사용
가능한 계약 테스트를 정의한다. 테스트는 실제 인터넷에 접근하지 않는다.

## 2. 계층과 HTTP 경계

- 실제 Adapter는 `com.econpulse.news.infrastructure.provider` 아래에 두고 `NewsProvider`를 구현한다.
- Provider별 요청·응답 DTO, `RestClient`, HTTP status와 client 예외는 infrastructure 밖으로 내보내지 않는다.
- application과 domain은 `NewsSearchQuery`, `NewsSearchResult`, `NewsProviderArticle`, `NewsProviderException`만 안다.
- 단일 외부 Adapter를 예상하므로 별도 HTTP Gateway는 만들지 않고 Adapter 내부에서 Spring `RestClient`를 직접 사용한다.
- 테스트는 test scope의 OkHttp `MockWebServer`로 localhost HTTP 응답을 재현한다.

## 3. 설정 계약

```yaml
econpulse:
  news:
    provider:
      type: none
      base-url: ${NEWS_PROVIDER_BASE_URL:}
      connect-timeout: ${NEWS_PROVIDER_CONNECT_TIMEOUT:2s}
      read-timeout: ${NEWS_PROVIDER_READ_TIMEOUT:3s}
```

기본 type은 `none`이며 운영 기본 profile에서 Fake Provider를 선택하지 않는다.
local profile만 type `fake`를 사용한다. 실제 Adapter 추가 시 base URL과 timeout을
위 설정에서 주입하고 무제한 timeout을 허용하지 않는다. 테스트는 수십 ms의 짧은
timeout을 사용해 느린 테스트를 피한다.

## 4. 요청 매핑 계약

- 내부 query는 URL template 변수로 인코딩해 공백, 한글, `+` 같은 문자를 보존한다.
- page가 외부 offset 기반이면 1-based `page * size + 1`로 변환한다.
- size는 외부 결과 크기 파라미터로 전달한다.
- `RECENCY`는 외부 최신순 값, `RELEVANCE`는 외부 관련도순 값으로 명시적으로 매핑한다.
- Provider별 파라미터 이름과 sort 값은 실제 Adapter 계약 테스트 subclass에서 검증한다.

## 5. 응답 매핑 계약

외부 응답은 Adapter 내부 DTO를 거쳐 다음 Port 필드로 변환한다.

| 필드 | 정책 |
|---|---|
| provider article id | 필수, null·blank 거부 |
| title | 필수, HTML 정제 후 blank면 거부 |
| summary | 선택, null은 빈 문자열로 변환 |
| sourceName | 선택, 없으면 정규 source URL의 host 사용 |
| sourceUrl | 필수, null·blank 거부 |
| publishedAt | 필수, ISO 8601 UTC `Instant` 변환 실패 시 거부 |
| totalElements | 제공되지 않으면 `OptionalLong.empty()` |
| page, size | 원래 내부 요청 값 유지 |
| hasNext | 외부 응답 메타데이터 유지 |

필수 필드 누락, 빈 title, 잘못된 날짜, malformed JSON은 모두
`INVALID_RESPONSE`이며 재시도하지 않는다. 임의의 빈 문자열을 정상 기사로 저장하지
않는다.

## 6. 짧은 외부 문자열 정제

`ExternalNewsTextSanitizer`는 Adapter가 Port 모델을 만들기 전에 제목과 요약에
다음을 적용한다.

- `<b>`를 포함한 일반 HTML 태그 제거
- `&quot;`, `&amp;`, `&lt;`, `&gt;`, `&#39;`, `&apos;`, `&nbsp;` 변환
- 연속 공백을 한 칸으로 정리하고 trim
- null summary 유지

이는 짧은 API 문자열 정제용이며 웹 문서 파서나 원문 HTML sanitizer가 아니다.

## 7. 오류와 재시도 계약

| 외부 상황 | `NewsProviderErrorType` | 재시도 |
|---|---|---|
| 401, 403 | `AUTHENTICATION_FAILED` | 아니오 |
| 429 | `RATE_LIMITED` | 예 |
| connection/read timeout | `TIMEOUT` | 예 |
| 500, 502, 503, 504 | `TEMPORARY_FAILURE` | 예 |
| 연결 실패 | `TEMPORARY_FAILURE` | 예 |
| 400, malformed JSON, 필수 필드 누락, 잘못된 날짜 | `INVALID_RESPONSE` | 아니오 |

외부 HTTP client 예외를 직접 던지지 않는다. `NewsProviderException` 메시지는 짧고
고정된 내부 문구만 사용하며 API 키, 전체 응답 본문, client 예외 클래스명, stack
trace를 포함하지 않는다. 원본 예외를 cause로 외부 경계에 보존하지 않는 정책을
계약 테스트가 검증한다.

## 8. 테스트 구조와 fixture

- `AbstractHttpNewsProviderContractTest`: 정상·빈 결과, 요청 매핑, HTML, status, timeout, 연결 실패, 보안 공통 계약
- `ReferenceHttpNewsProviderContractTest`: test-only reference Adapter 생성과 외부 파라미터 검증
- `NewsProviderContractTestSupport`: `src/test/resources/news-provider` fixture 로딩
- `ReferenceHttpNewsProviderAdapter`: 운영 코드가 아닌 test-only RestClient 경계 예제

향후 실제 Adapter 테스트는 추상 계약을 상속하고 `createProvider`와
`assertMappedRequest`를 구현한다. Provider 응답 형태가 다르면 같은 의미를 가진
Provider별 fixture 경로를 override하도록 계약을 확장한다. 자체 테스트 프레임워크나
실제 인터넷 호출은 추가하지 않는다.

현재 fixture는 success, empty, HTML/null summary, missing title/source URL/date,
blank title, invalid date, malformed JSON, 비밀값이 포함된 오류 본문을 포함한다.
