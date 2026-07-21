# 운영 Health 정책

## 범위

Phase 5 첫 단계는 Spring Boot Actuator 기본 기능으로 애플리케이션 상태를 제공한다.
애플리케이션과 같은 포트를 사용하고 web endpoint는 `health`와 `info`만 노출한다.
별도 management port, Spring Security 인증과 네트워크 ACL은 아직 구현하지 않았다.

## Endpoint

- `/actuator/health`: 전체 상태와 probe group 이름
- `/actuator/health/liveness`: `livenessState`만 포함
- `/actuator/health/readiness`: `readinessState`, `db`, `redis` 포함
- `/actuator/info`: 현재 비민감 빈 객체 가능

`show-details=never`이므로 DB URL·사용자명, Redis host·port, Naver endpoint·자격 증명,
상세 예외와 stack trace를 공개하지 않는다. UP은 200, DOWN은 Spring Boot 기본 매핑인
503을 사용한다. env, beans, configprops, heapdump, threaddump, loggers, mappings,
scheduledtasks, conditions, metrics와 prometheus는 web에 노출하지 않는다.

## Liveness와 Readiness

Liveness는 애플리케이션 프로세스 자체가 요청을 처리할 수 있는지를 나타낸다. MySQL,
Redis와 Naver 장애는 liveness를 DOWN으로 만들지 않는다. Spring Boot 기본
`LivenessStateHealthIndicator`를 사용하고 커스텀 indicator는 두지 않는다.

Readiness는 공개 API를 정상 처리할 필수 저장소인 MySQL과 Redis를 포함한다. MySQL 또는
Redis가 DOWN이면 readiness도 DOWN이다. 인기 상세 기록은 Redis 장애에 fail-open하지만
공개 인기 순위 API는 Redis가 필요하므로 Redis를 readiness 필수 의존성으로 둔다.

Naver는 경제용어와 인기 순위 등 전체 공개 API의 필수 의존성이 아니다. Provider 오류는
기능별 기존 502·503 계약으로 처리하며 readiness와 애플리케이션 기동 과정에서 Naver를
호출하지 않는다. 외부 Provider 장애는 안전한 분류 로그와 실제 HTTP 요청 기준의 제한된
오류 메트릭을 남긴다. 메트릭 수집은 readiness dependency가 아니다.

Micrometer 메트릭은 내부 MeterRegistry에 등록하지만 web exposure는 변경하지 않는다.
`/actuator/metrics`와 `/actuator/prometheus`는 404이며 세부 정책은
`docs/11-operational-metrics.md`에 있다.

## Probe 경로

Kubernetes 배포 구성에서 다음 경로를 사용할 수 있다.

```text
livenessProbe:  /actuator/health/liveness
readinessProbe: /actuator/health/readiness
```

이번 작업은 Kubernetes manifest나 애플리케이션 Compose 서비스를 추가하지 않으며 기존
MySQL·Redis Compose healthcheck도 변경하지 않는다.
