# Continuous Integration

## Workflow와 실행 조건

`.github/workflows/ci.yml`의 `CI` workflow는 단일 `verify` Job으로 다음 경우 실행한다.

- `main` branch push
- 모든 `pull_request`
- 수동 `workflow_dispatch`

tag push, schedule과 path filter는 없다. 문서만 변경해도 CI를 실행한다. 같은 pull request
또는 같은 branch에 새 commit이 오면 `concurrency` group이 이전 실행을 취소한다. 서로 다른
pull request는 서로 취소하지 않는다.

## 실행 환경과 Actions

- GitHub-hosted `ubuntu-latest`
- job timeout 30분
- Eclipse Temurin Java 17
- 저장소의 Gradle Wrapper 8.14.5
- `actions/checkout@v6`
- `actions/setup-java@v5`
- `gradle/actions/wrapper-validation@v6`
- `gradle/actions/setup-gradle@v6`
- `actions/upload-artifact@v7`

Gradle 공식 Action의 기본 cache 정책을 사용한다. 기본 branch 실행만 cache를 쓰고 PR은
cache를 읽는 공식 정책을 유지하며 `actions/cache`나 `setup-java cache`를 중복 사용하지
않는다. 별도 wrapper-validation step이 검증 책임을 가지므로 setup-gradle의 중복 wrapper
검증만 끈다. CI는 wrapper를 생성·갱신하지 않고 항상 executable bit가 저장된 `./gradlew`를
사용한다.

공식 major tag는 보안·런타임 수정이 같은 major에 반영되어 유지 관리가 간단하지만 tag가
가리키는 commit이 이동할 수 있다. commit SHA 고정은 재현성과 공급망 방어가 강한 대신
공식 업데이트를 수동으로 추적해야 한다. 현재 단계는 검증된 공식 major tag를 사용하며
SHA 고정은 운영 정책 backlog다.

## 검증 경계

Job은 다음 순서로 fail-fast 실행한다.

1. Gradle Wrapper JAR와 distribution URL 검증
2. `bash -n scripts/*.sh`
3. `docker compose config`
4. `git diff --check`
5. `./gradlew clean build --no-daemon --stacktrace`
6. 성공·실패와 관계없이 품질 report artifact 업로드

Gradle `build`는 compile, 단위·통합·E2E와 ArchUnit 테스트, MySQL·Redis Testcontainers,
Checkstyle, JaCoCo report와 coverage verification을 포함한다. `--info`와 `--debug`는 기본으로
사용하지 않는다. HTML/XML report는 Gradle 기본 `build/reports` 아래에 생성된다.

Compose 단계는 문법과 기본 placeholder 구성을 확인할 뿐 서비스를 실행하지 않는다.
통합 테스트는 Ubuntu runner의 Docker에서 기존 Testcontainers가 `mysql:8.0`과
`redis:7-alpine`을 직접 관리한다. Compose service container, 고정 localhost DB, H2와
Ryuk 비활성화는 사용하지 않는다. 실제 Naver API도 호출하지 않는다.

## 권한과 pull request 보안

Workflow 최상위 권한은 `contents: read`뿐이다. write 권한, OIDC, package publish,
Dependency Submission과 PR comment 권한이 없다. repository Secret과 `.env`를 사용하지
않고 테스트의 Fake/Mock Provider와 Testcontainers만 사용한다.

이 workflow는 `pull_request` merge commit의 신뢰할 수 없는 코드를 실행할 수 있으므로
GitHub Token은 read-only이고 Secret이 없다. `pull_request_target`은 사용하지 않는다.
checkout, Gradle build와 repository shell script가 외부 변경 코드라는 전제에서 배포나
외부 시스템 mutation을 수행하지 않는다.

## Report artifact

`verification-reports-${github.run_id}` artifact에 다음만 7일 보관한다.

- `build/reports/tests/**`
- `build/reports/jacoco/**`
- `build/reports/checkstyle/**`

`if: always()`로 실패 분석에도 report를 남기며 report가 생성되기 전 실패했다면
`if-no-files-found: ignore`로 artifact step 자체가 원래 실패를 가리지 않는다. JAR, 로그,
credential과 전체 build output은 업로드하지 않는다.

## 로컬 재현

일상적인 변경 후 저장소 표준 진입점은 다음과 같다.

```bash
./scripts/check.sh
```

CI와 동일한 build 경계를 재현할 때는 다음을 실행한다.

```bash
bash -n scripts/*.sh
docker compose config
git diff --check
./gradlew clean build --no-daemon --stacktrace
```

`check.sh`는 shell syntax, `clean check`, Compose config를 한 번씩 실행한다. CI는 배포 가능한
artifact 조립까지 검증하기 위해 `clean build`를 사용한다. 두 경로의 test, Checkstyle,
JaCoCo와 ArchUnit 기준은 같은 Gradle task graph를 사용한다.

## 실패 확인 순서

1. 실패한 GitHub Actions step을 확인한다.
2. Wrapper validation, Java 17과 Gradle 8.14.5 설정을 확인한다.
3. 7일 보관되는 test·JaCoCo·Checkstyle report artifact를 확인한다.
4. Testcontainers 실패면 Ubuntu Docker와 MySQL·Redis container 로그를 확인한다.
5. 같은 명령을 로컬 Docker 환경에서 실행한다.
6. 코드를 수정해 원인을 제거하고 검증한다. 원인 분석 없이 재실행만 반복하지 않는다.
7. requestId 기반 런타임 추적과 compile/test/quality-gate CI 오류를 구분한다.

로컬 정적 검증은 원격 runner와 event context를 완전히 재현하지 않는다. push 후 첫 실행에서
trigger, concurrency 취소, cache, wrapper validation, Testcontainers와 artifact 업로드를
GitHub Actions UI에서 확인해야 한다.
