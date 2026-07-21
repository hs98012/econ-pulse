# EconPulse

경제용어 사전에 최신 뉴스를 연결하고 Redis 기반 일간 인기 경제용어를 제공하는
Spring Boot 백엔드입니다. EconPulse backend MVP와 Phase 5 핵심 운영 준비 범위는
완료됐습니다. 자동 테스트·CI와 로컬 클린 환경 재현까지 검증했으며, 실제 운영 배포와
외부 운영 인프라는 backlog입니다.

현재 버전은 `1.0.0`입니다. 이 표시는 MVP 범위 완료를 뜻하며 무중단 운영이나
production-ready 배포 환경을 보장한다는 의미는 아닙니다.

## 주요 기능

- 경제용어 CRUD, ACTIVE 목록·이름/별칭 검색과 상세 조회
- Provider-neutral 뉴스 수집, URL hash 기반 멱등 저장과 뉴스 목록·상세 조회
- Fake/Naver Provider Adapter, 순수 용어 매처와 멱등 자동 매핑
- 용어별 관련 뉴스 최신순 공개 조회
- Redis UTC 일간 인기 점수, 상세 조회 기록과 인기 순위 공개 API
- Redis 기록 장애 fail-open과 인기 조회 장애 503 분리
- Actuator health·liveness·readiness, `X-Request-Id`, 구조화 로그
- Micrometer 핵심 메트릭, MySQL 실행계획 점검, GitHub Actions CI

## 기술 스택

- Java 17, Spring Boot 3.5.15, Gradle Wrapper 8.14.5
- Spring Web, Data JPA, Data Redis, Validation, Actuator, Micrometer Core
- MySQL 8.0, Redis 7, Flyway, Docker Compose
- JUnit 5, Testcontainers, MockWebServer, ArchUnit, Checkstyle, JaCoCo

## 아키텍처

기능 단위 `term`, `news`, `mapping`, `popular`, `global` 패키지 안에서 API → Application
→ Domain/Infrastructure 방향을 유지합니다. 외부 뉴스와 Redis는 Application Port 뒤의
Adapter로 연결하며, Application·Domain은 RedisTemplate과 Micrometer 타입에 직접
의존하지 않습니다. API는 JPA Entity를 반환하지 않습니다.

## 빠른 시작

필수 도구는 Java 17, 실행 중인 Docker와 Docker Compose plugin입니다.

```bash
git clone <repository-url> econ-pulse
cd econ-pulse
cp .env.example .env
./scripts/run-local.sh
```

`.env.example`에는 로컬 placeholder만 있으며 실제 Naver 자격 증명은 필요하지 않습니다.
`run-local.sh`는 MySQL·Redis health를 기다린 뒤 local profile 애플리케이션을 foreground로
실행합니다. `Ctrl-C`로 애플리케이션을 종료하고 인프라는 volume을 보존해 종료합니다.

```bash
docker compose down
```

## Health 확인

```bash
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8080/actuator/health/liveness
curl -i http://localhost:8080/actuator/health/readiness
```

Readiness는 MySQL과 Redis를 포함하고 Naver는 포함하지 않습니다. 공개 Actuator endpoint는
`health`, `info`뿐이며 `/actuator/metrics`와 `/actuator/prometheus`는 노출하지 않습니다.

## Smoke test와 클린 재현

기존 개발 volume을 건드리지 않는 권장 검증은 다음과 같습니다.

```bash
./scripts/verify-clean-environment.sh
```

별도 Compose project·포트와 임시 저장소 복사본에서 빈 MySQL Flyway migration,
애플리케이션 readiness, Fake 뉴스 수집, 자동 매핑·관련 뉴스, 상세 조회·인기 순위,
재기동과 cleanup을 검증합니다. 쓰기 smoke를 공유 DB나 운영 서버에 실행하지 마십시오.

## 전체 검증

```bash
bash -n scripts/*.sh
docker compose config
./scripts/check.sh
git diff --check
```

`check.sh`는 Gradle test, Checkstyle, JaCoCo, ArchUnit과 MySQL·Redis Testcontainers 테스트를
포함합니다. CI는 main push, 모든 pull request와 수동 실행에서 같은 품질 기준을 검증하며
main push 기준 실제 성공을 확인했습니다.

## 문서

- [제품 요구사항](docs/01-product-requirements.md)
- [도메인 모델](docs/02-domain-model.md)
- [API 계약](docs/03-api-spec.md)
- [DB·Redis 스키마](docs/04-db-schema.md)
- [Phase별 개발 계획](docs/05-development-plan.md)
- [뉴스 Provider 계약](docs/06-news-provider-adapter-contract.md)
- [용어-뉴스 매칭 정책](docs/07-term-news-matching-policy.md)
- [인기 용어 정책](docs/08-popular-term-policy.md)
- [운영 Health](docs/09-operational-health.md)
- [운영 로깅](docs/10-operational-logging.md)
- [운영 메트릭](docs/11-operational-metrics.md)
- [DB 성능 분석](docs/12-database-performance.md)
- [CI 운영](docs/13-continuous-integration.md)
- [로컬·운영 실행 Runbook](docs/14-local-and-operational-runbook.md)
- [후속 Backlog](docs/15-backlog.md)

## 완료 상태와 backlog

Phase 0부터 Phase 5까지 완료했습니다. 완료 범위는 백엔드 MVP, 핵심 운영 관측,
자동 테스트·CI, DB 성능 점검과 로컬 클린 재현입니다. PopularTermSnapshot, 스케줄러,
실제 Naver credential smoke, Prometheus·Grafana, 이미지 배포·CD와 Kubernetes 등은
[후속 Backlog](docs/15-backlog.md)에 분리했습니다.
