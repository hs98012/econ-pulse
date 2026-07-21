# 로컬·운영 실행 Runbook

## 범위와 필수 환경

이 문서는 처음 받은 저장소를 기존 DB·Redis, `.env`, build 결과와 Naver 자격 증명 없이
실행하는 절차를 고정한다. 제품 배포 자동화, Docker image publish와 클라우드 인프라는
범위가 아니다.

- Java 17
- 실행 중인 Docker와 Docker Compose plugin
- Git checkout과 Gradle Wrapper
- HTTP smoke에는 `curl`
- 기본 포트: application 8080, MySQL host 3308, Redis 6379

저장소의 `mysql:8.0`과 `redis:7-alpine` 이미지는 버전 계열이 명시돼 있다. MySQL은
`utf8mb4`/`utf8mb4_unicode_ci`, 두 서비스 모두 healthcheck와 project-scoped named volume을
사용한다. `container_name`이 없으므로 서로 다른 Compose project를 동시에 격리할 수 있다.

## 첫 실행

```bash
git clone <repository-url> econ-pulse
cd econ-pulse
cp .env.example .env
./scripts/run-local.sh
```

`.env.example`은 로컬 placeholder만 포함한다. `.env`는 Git에서 제외되며 스크립트가 값을
출력하지 않는다. `run-local.sh`는 Java 17, Docker daemon과 Compose plugin을 검사하고
MySQL·Redis를 시작해 health를 기다린 뒤 local profile 애플리케이션을 foreground로
실행한다. 다른 터미널에서 다음을 확인한다.

```bash
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8080/actuator/health/liveness
curl -i http://localhost:8080/actuator/health/readiness
```

모두 UP이면 `X-Request-Id` 응답 헤더가 있고 component 상세는 없다. `Ctrl-C`는 실행한
애플리케이션만 종료한다. 인프라는 다음처럼 volume을 보존해 종료한다.

```bash
docker compose down
```

## 설정 책임

### local

- 사람이 읽기 쉬운 requestId 상관 로그
- Compose MySQL·Redis
- 결정적인 두 기사 fixture를 가진 Fake Provider
- 뉴스 동기화·mapping rebuild·단일 뉴스 자동 매핑 내부 API 기본 비활성
- 내부 API는 격리 작업에서 해당 환경변수를 `true`로 지정할 때만 활성

### test

- MySQL·Redis Testcontainers와 테스트별 독립 데이터
- Mock/Fake Provider 및 실제 외부 HTTP 호출 금지
- 필요한 고정 Clock과 test property
- 동일 Checkstyle·JaCoCo·ArchUnit 품질 기준

### 운영 기본

- Logstash JSON console 로그와 환경변수 기반 MySQL·Redis 설정
- Provider 기본 `none`; Naver 선택 시에만 외부 주입한 Client ID·Secret 사용
- 내부 API 기본 비활성
- health detail 비노출, Actuator web endpoint `health,info`만 노출
- Micrometer는 내부 MeterRegistry에만 기록하고 `/actuator/metrics`와
  `/actuator/prometheus`는 비노출
- Secret 파일·저장소 commit 및 로그 출력 금지

## Flyway와 재기동

애플리케이션 최초 기동이 빈 schema에 V1과 V2를 순서대로 적용한다. Hibernate
`ddl-auto=validate`가 migration schema와 Entity 계약을 검증한다. 두 번째 기동은 같은
`flyway_schema_history`를 읽고 migration을 다시 적용하지 않는다. 기존 migration을
수정하거나 운영에서 Flyway clean을 활성화하지 않는다.

2026-07-21 클린 검증에서는 빈 MySQL volume에 성공 migration 2건을 확인한 뒤 같은 volume로
애플리케이션을 재기동했고 이력이 2건으로 유지됐다.

## 격리된 클린 환경 검증

Java 17을 현재 셸에 선택한 뒤 실행한다.

```bash
./scripts/verify-clean-environment.sh
```

고정 기본 포트가 사용 중이면 운영 포트가 아닌 빈 로컬 포트로 덮어쓴다.

```bash
CLEAN_MYSQL_PORT=43308 \
CLEAN_REDIS_PORT=46379 \
CLEAN_SERVER_PORT=48080 \
./scripts/verify-clean-environment.sh
```

스크립트는 `econpulse-clean-<pid>` project와 `mktemp` 저장소 복사본을 사용하며 `.git`,
`.gradle`, `build`를 복사하지 않는다. 빈 Gradle home, 새 MySQL·Redis volume에서 다음을
수행한다.

1. Compose service health 대기
2. local Fake Provider와 뉴스 동기화·단일 매핑 API만 임시 활성
3. 애플리케이션 PID를 기록하고 readiness를 최대 180초 polling
4. `smoke-test.sh` 실행
5. Flyway 성공 이력 확인
6. 애플리케이션만 종료하고 같은 DB로 재기동
7. readiness와 Flyway 이력 비중복 확인
8. 자신이 만든 process, container, network, volume 정리

성공하면 임시 로그까지 제거한다. 실패하면 원인 확인을 위해 임시 경로를 출력하고
애플리케이션 로그를 남기지만 Compose 자원은 정리한다. 기존 기본 project와 volume에는
`down`이나 `-v`를 실행하지 않는다.

## HTTP smoke 범위

`smoke-test.sh`는 DB 쓰기를 하므로 단독 실행에는 명시적 opt-in이 필요하다.

```bash
ALLOW_SMOKE_WRITE=true \
BASE_URL=http://localhost:8080 \
./scripts/smoke-test.sh
```

공유 개발 DB와 운영 서버에서는 실행하지 않는다. 권장 진입점은 위 클린 검증 스크립트다.
smoke는 고정 sleep 대신 readiness를 최대 60초 polling하고 다음을 검증한다.

- health·liveness·readiness 200/UP, 상세 비노출과 `X-Request-Id`
- `/actuator/metrics`, `/actuator/prometheus` 404
- 기준금리 용어 생성, 목록·`base rate` 별칭 검색·상세 조회
- 상세 조회 후 Redis 오늘 인기 score 1 이상과 rank
- local Fake 기사 2건 수집, 실제 Naver 호출 없음
- 뉴스 한 건 자동 매핑 `created=1`, 반복 호출 `skipped=1`
- 관련 뉴스 `totalElements=1`로 매핑 멱등성 확인

테스트 데이터는 개별 delete API로 정리하지 않고 disposable project volume 전체와 함께
제거한다.

## 전체 품질 검증과 CI 차이

```bash
bash -n scripts/*.sh
docker compose config
./scripts/check.sh
git diff --check
```

CI는 build, 단위·통합·E2E, Checkstyle, JaCoCo, ArchUnit, Testcontainers, shell syntax와
Compose config를 검증한다. 클린 검증은 여기에 실제 Compose 인프라, 빈 volume Flyway,
실제 Spring process, readiness, HTTP smoke, 재기동과 cleanup을 추가한다. 이번 단계에서는
클린 검증을 GitHub Actions에 추가하지 않는다. 실행 시간이 안정화된 뒤 Secret 없는 수동
workflow로 검토할 수 있다.

## 데이터 초기화

일상 종료는 `docker compose down`으로 volume을 보존한다. 다음 명령은 지정한 project의
MySQL과 Redis volume을 모두 삭제하고 새로 시작하므로 개발 데이터가 필요 없음을 먼저
확인해야 한다.

```bash
ALLOW_DATA_RESET=true \
COMPOSE_PROJECT_NAME=econ-pulse \
./scripts/reset-db.sh
```

opt-in이 없거나 project 이름이 안전한 문자 규칙에 맞지 않으면 스크립트는 아무것도
삭제하지 않고 실패한다. 운영 DB에 사용하지 않는다. 클린 재현에는 이 스크립트 대신
격리 검증을 사용한다.

## 로그·메트릭·장애 확인

운영 문의는 응답 `X-Request-Id`로 `http_request_completed`와 기능별 오류 로그를 찾는다.
로그에 요청 body, query string, 인증 header, DB·Redis password와 Naver credential을
남기지 않는다. 기동 실패는 Flyway → MySQL → Redis 순으로 확인하고, readiness DOWN이면
`docker compose ps`와 해당 service log를 확인한다.

인기 상세 기록 Redis 실패는 warning 후 상세 200인 fail-open이고 인기 순위 조회 Redis
실패는 503이다. Naver는 readiness 의존성이 아니다. 메트릭은 성공률·처리량·지연 추세,
requestId 로그는 개별 오류 추적에 사용한다. 세부 정책은 운영 health, logging, metrics와
DB 성능 문서(`docs/09`~`docs/12`)를 따른다.
