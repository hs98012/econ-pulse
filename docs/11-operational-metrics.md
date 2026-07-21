# 운영 메트릭 정책

## 범위와 수집 구조

Phase 5 핵심 비즈니스 메트릭은 Actuator가 제공하는 Micrometer Core와 Spring의
`MeterRegistry`에 등록한다. Application Service는 기능별 Metrics Port만 호출하고,
infrastructure의 Micrometer Adapter가 Counter와 Timer를 기록한다. Prometheus Registry,
Grafana, 알림, OpenTelemetry와 분산 추적은 현재 범위가 아니다.

## 메트릭 목록

| 이름 | Type | Tag와 허용값 | 측정 경계 |
| --- | --- | --- | --- |
| `econpulse.news.ingestion.runs` | Counter | `outcome=success\|failure` | 뉴스 수집 실행 완료 |
| `econpulse.news.ingestion.duration` | Timer | `outcome=success\|failure` | 수집 Application 경계 전체 |
| `econpulse.news.ingestion.articles` | Counter | `result=fetched\|created\|updated\|skipped` | commit된 성공 결과의 실제 건수 |
| `econpulse.news.provider.requests` | Counter | `provider=naver`, `outcome=success\|failure`, `error=none\|authentication\|rate_limit\|timeout\|temporary_failure\|bad_response\|invalid_request` | 실제 Naver HTTP 요청 |
| `econpulse.news.provider.duration` | Timer | `provider=naver`, `outcome=success\|failure` | 실제 Naver HTTP 요청 전체 |
| `econpulse.term_news.mapping.runs` | Counter | `outcome=success\|failure` | `mapNews` 단일 뉴스 자동 매핑 |
| `econpulse.term_news.mapping.duration` | Timer | `outcome=success\|failure` | 단일 뉴스 자동 매핑 전체 |
| `econpulse.term_news.mapping.results` | Counter | `result=created\|updated\|skipped\|no_match` | commit된 성공 결과의 실제 건수 |
| `econpulse.popular_term.record` | Counter | `outcome=success\|unavailable` | 상세 조회 성공 뒤 Redis 기록 |
| `econpulse.popular_term.query` | Counter | `outcome=success\|unavailable\|failure` | Redis 순위와 ACTIVE MySQL 용어 결합 조회 |
| `econpulse.popular_term.query.duration` | Timer | `outcome=success\|unavailable\|failure` | 인기 순위 Application 조회 전체 |

빈 결과도 정상 완료하면 success다. 수집과 단일 뉴스 매핑의 성공 실행 및 결과 Counter는
트랜잭션 commit 뒤 기록한다. rollback이면 부분 결과 Counter 없이 failure 실행과 Timer만
기록한다. Provider page/start 사전 검증처럼 HTTP 전송 전에 실패하면 Provider HTTP
메트릭에 포함하지 않는다. Fake Provider도 외부 HTTP 메트릭 대상이 아니다.

## 장애와 반환 계약

상세 조회 뒤 Redis 기록 성공은 `record outcome=success`, 예상한 store unavailable은
`record outcome=unavailable`이다. unavailable은 warning 로그와 함께 기록하지만 상세
응답은 200인 fail-open을 유지한다. `INVALID_DATA`나 예상하지 못한 RuntimeException은
unavailable로 숨기지 않는다. 용어 조회 자체가 실패하면 record 메트릭도 증가하지 않는다.

인기 순위 조회의 Redis `UNAVAILABLE`은 query `unavailable`이며 기존
`503 POPULAR_TERM_STORE_UNAVAILABLE` 계약을 유지한다. MySQL이나 예상하지 못한 오류는
query `failure`로 기록하고 기존 오류 처리로 전파한다.

## Cardinality 정책

Tag에는 표의 닫힌 분류만 사용한다. requestId, economicTermId, newsArticleId, 검색어,
뉴스 제목, URL, 사용자 IP, 예외 메시지·전체 클래스명, HTTP path, Redis key, 날짜, limit과
Client ID를 넣지 않는다. requestId는 요청마다 증가하는 로그 상관관계 값이므로 메트릭
tag로 쓰면 time series가 요청 수만큼 늘어난다.

## 노출과 운영 해석

메트릭은 내부 `MeterRegistry`에만 등록된다. Actuator web exposure는 `health,info`이며
`/actuator/metrics`와 `/actuator/prometheus`는 공개하지 않는다. 향후 운영 접근 정책을
정한 뒤 Prometheus Registry와 dashboard를 별도 작업으로 연결할 수 있다.

- news ingestion failure 증가: Provider 오류 메트릭과 requestId 로그를 확인한다.
- popular term unavailable 증가: Redis readiness와 Redis 장애 로그를 확인한다. record는
  상세 응답 fail-open, query는 503이라는 차이를 함께 본다.
- mapping duration 증가: ACTIVE 용어 수와 DB 쿼리 성능을 확인한다.
- Provider timeout·temporary_failure 증가: 외부 장애와 네트워크 상태를 확인한다.
