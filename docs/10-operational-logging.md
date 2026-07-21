# 운영 요청 추적과 로깅 정책

## 요청 ID

모든 HTTP 요청은 `X-Request-Id`를 사용한다. `[A-Za-z0-9._-]{8,128}`에 맞는 클라이언트
값은 재사용하고, 헤더가 없거나 빈 값·짧은 값·129자 이상·허용되지 않은 문자나 제어
문자를 포함하면 UUID를 생성한다. 사용자·IP·세션·DB ID와 timestamp 단독 값은 생성에
사용하지 않는다. 최종 값은 성공과 오류를 포함한 모든 응답 헤더에 설정한다.

Filter는 기존 MDC 값을 보존한 뒤 동기 요청 범위에 `requestId`만 등록한다. 완료 로그를
남긴 다음 `finally`에서 기존 값을 복원하거나 제거한다. Controller와 응답 DTO는 요청 ID를
전달하거나 보관하지 않는다. 비동기 MDC 전파, TaskDecorator, Reactor Context, traceId와
spanId는 후속 비동기·분산 추적 작업으로 분리한다.

## 로그 형식과 완료 이벤트

운영 기본 console 로그는 Spring Boot 내장 Logstash JSON 형식이다. 최소 공통 필드는
timestamp, level, logger, thread, message와 MDC requestId다. local profile은 기존 가독성
있는 console pattern을 유지하면서 `[requestId=...]`를 표시한다.

요청마다 `http_request_completed` 이벤트를 정확히 한 번 기록한다. 이벤트 필드는 method,
query string을 제외한 URI path, 최종 HTTP status와 monotonic `nanoTime` 기반 durationMs다.
2xx·3xx는 INFO, 4xx는 WARN, 5xx는 ERROR다. 완료 이벤트에는 stack trace를 넣지 않는다.

## 오류와 기능별 로그

- 예상 가능한 4xx는 WARN `http_request_error`로 오류 코드, method와 path만 기록하며
  stack trace를 남기지 않는다.
- Provider·인기 저장소 같은 예상 장애는 WARN으로 안전한 오류 분류와 retryable 여부 등만
  기록하고 외부 예외 문자열과 응답 body를 남기지 않는다.
- 예상하지 못한 500은 `unexpected_http_error` ERROR에서 stack trace를 한 번만 기록한다.
  사용자 응답은 기존 `INTERNAL_SERVER_ERROR`를 유지한다.
- 인기 점수 기록 unavailable은 `popular_term_record_failed` WARN으로 non-sensitive
  economicTermId와 `reason=unavailable`만 기록하고 상세 API fail-open을 유지한다.
- Naver를 포함한 Provider 장애 로그에는 검색 query, 요청 URL query, 기사 내용, Client ID,
  Client Secret, 인증 헤더와 외부 응답 body를 포함하지 않는다.

## 기록하지 않는 데이터

요청·응답 body, 전체 query string, Authorization, Cookie, Set-Cookie, API key, Naver
자격 증명, DB URL, Redis 접속 비밀과 전체 header dump를 금지한다. 현재 path variable은
URI path에 남을 수 있다. 향후 사용자 식별 정보가 path에 추가되면 별도 마스킹 정책을
설계한다. 로그 수집 서버와 비동기 appender는 이번 범위에 포함하지 않는다.

## 장애 분석 절차

1. 사용자 또는 모니터링 응답의 `X-Request-Id`를 확인한다.
2. 같은 requestId의 `http_request_completed` 로그를 찾는다.
3. 동일 requestId의 Application·Infrastructure 오류 이벤트를 확인한다.
4. HTTP status와 durationMs를 확인한다.
5. MySQL·Redis readiness 상태를 확인한다.

## 메트릭과의 역할 분리

로그는 requestId로 개별 요청과 오류 원인을 추적하고, 메트릭은 제한된 tag로 처리량,
성공률, 오류율과 지연 추세를 본다. 동일 장애가 warning/error 로그와 Counter에 함께
나타날 수 있지만 Counter 때문에 로그를 중복 출력하지 않는다. requestId, 로그 메시지,
예외 메시지와 stack trace는 메트릭 tag로 복사하지 않는다. 전체 정책은
`docs/11-operational-metrics.md`에 있다.
