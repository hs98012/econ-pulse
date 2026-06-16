# EconPulse

경제용어 사전에 최신 뉴스를 자동 매핑하고 Redis 기반 실시간 인기 검색어를
제공하는 Spring Boot 백엔드입니다.

현재 구현 범위는 Phase 1 프로젝트 및 인프라 기반입니다. Entity, Controller,
API는 아직 구현하지 않습니다.

## 기술 스택

- Java 17
- Spring Boot 3.5.15
- Gradle Wrapper
- Spring Web, Spring Data JPA, Validation
- MySQL 8.0, Redis 7
- Lombok
- Docker Compose

## 로컬 실행

필수 도구:

- JDK 17
- Docker 및 Docker Compose

환경변수 기본값은 로컬 개발용으로 설정되어 있습니다. 값을 변경하려면 예제
파일을 기준으로 `.env`를 작성합니다.

```bash
cp .env.example .env
```

MySQL과 Redis를 실행합니다.

```bash
docker compose up -d
docker compose ps
```

로컬 MySQL과 다른 Docker MySQL이 기본 포트 `3306` 또는 인접 포트를 사용 중일
수 있으므로 Docker MySQL은 충돌을 피하기 위해 호스트 포트 `3308`로 실행합니다.
컨테이너 내부 포트는 `3306`입니다.

두 서비스가 `healthy` 상태가 된 뒤 애플리케이션을 실행합니다.

```bash
./gradlew bootRun
```

애플리케이션은 기본적으로 `http://localhost:8080`에서 실행됩니다. 현재
Phase에는 공개 API가 없습니다.

한 번에 인프라와 애플리케이션을 실행하려면 다음 스크립트를 사용할 수 있습니다.

```bash
./scripts/run-local.sh
```

## 검증

```bash
bash -n scripts/*.sh
./scripts/check.sh
docker compose config
```

로컬 데이터베이스와 Redis 볼륨을 초기화하려면 다음 명령을 실행합니다.

```bash
./scripts/reset-db.sh
```
