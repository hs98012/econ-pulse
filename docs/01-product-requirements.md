# Product Requirements

## 1. 프로젝트 목표

EconPulse는 경제용어 사전을 기반으로 최신 경제 뉴스를 자동 수집·매핑하고, 사용자의 검색 행동을 Redis에 집계하여 실시간 인기 검색어를 제공하는 Spring Boot 백엔드 서비스다.

## 2. 사용자 가치

- 사용자는 경제용어의 정의와 관련 최신 뉴스를 한 화면에서 이해할 수 있다.
- 사용자는 현재 관심이 높은 경제용어를 실시간 순위로 확인할 수 있다.
- 운영자는 뉴스 수집과 용어 매핑 결과를 추적하고 재처리할 수 있다.

## 3. 범위

### 포함

- 경제용어 등록, 조회, 검색
- 뉴스 메타데이터 수집 및 조회
- 제목·요약의 용어 기반 자동 매핑
- 검색 이벤트의 Redis 실시간 집계
- 인기 검색어 조회 및 MySQL 스냅샷 보관

### 초기 범위 제외

- 사용자 계정, 개인화, 댓글
- 뉴스 원문 전체 재배포
- 머신러닝 기반 의미 분석
- 관리자 UI

## 4. 기술 스택

Java 17, Spring Boot, Gradle, Spring Web, Spring Data JPA, MySQL 8.0, Redis 7, Lombok, Jakarta Validation, Docker Compose를 사용한다.

## 5. 기능 요구사항

1. 용어 검색은 이름과 별칭을 대상으로 하며 페이징을 지원한다.
2. 용어 상세 조회는 정의와 연결된 최신 뉴스를 제공한다.
3. 뉴스 수집은 외부 제공자와 분리된 어댑터를 통해 실행한다.
4. 자동 매핑은 일치 근거와 신뢰도 점수를 저장하며 중복 매핑을 만들지 않는다.
5. ACTIVE 경제용어의 공개 상세 조회 성공만 요청마다 인기 점수에 반영한다.
6. 인기 검색어는 점수 내림차순으로 반환하고 동점은 용어 ID 오름차순으로 정렬한다.

## 6. 비기능 요구사항

- API 오류는 일관된 JSON 형식과 적절한 HTTP 상태를 사용한다.
- 외부 뉴스 장애가 용어 조회 API를 중단시키지 않아야 한다.
- 인기 검색 집계는 Redis 장애 시 경고를 기록하고 핵심 검색 응답은 유지한다.
- 모든 시간은 UTC로 저장하고 API에서는 ISO 8601로 표현한다.
- 뉴스 URL, 용어명, 매핑 쌍은 저장소 수준에서 중복을 방지한다.

## 7. 제품 성공 기준

- 용어 검색부터 관련 뉴스 조회까지 API 계약 테스트가 통과한다.
- 동일 뉴스 재수집 및 동일 매핑 재실행이 멱등적이다.
- 상세 조회 성공 직후 해당 용어가 인기 순위에 반영된다.
- Docker Compose 환경에서 MySQL과 Redis를 포함한 통합 테스트가 재현 가능하다.

## 8. 구현 상태

- Phase 2 경제용어 사전은 완료했다.
- Phase 3 뉴스 Provider, 멱등 수집·조회, 순수 문자열 매칭, 멱등 매핑, 단일 뉴스 자동
  매핑과 용어별 관련 뉴스 공개 조회를 완료했다.
- Fake Provider → 뉴스 저장 → 자동 매핑 → 공개 관련 뉴스 조회의 핵심 흐름과 반복 실행
  멱등성을 MySQL Testcontainers E2E로 검증했다.
- 실제 운영 Naver 자격 증명 smoke, 뉴스별 연결 용어 조회, 무제한·날짜 범위 재처리,
  비동기 Job과 스케줄러는 운영 개선 backlog다.
- Phase 4는 완료했다. Redis Sorted Set 기반 UTC 일간 용어 ID·상세 조회 횟수 저장 경계,
  원자적 기록, Redis 순위와 ACTIVE MySQL 용어 정보를 결합하는 Application 조회를 구현했다.
- UTC 오늘의 인기 경제용어 공개 API를 구현했다. ACTIVE 용어만 반환하며 Redis에만 있는
  ID와 INACTIVE 용어를 제외한 뒤 rank를 재계산하고, Redis 장애는 503으로 반환한다.
- 공개 경제용어 상세 조회 성공 후 해당 용어의 UTC 오늘 점수를 요청마다 1 증가시킨다.
  기록 Redis 장애는 상세 조회를 실패시키지 않는 fail-open이며 목록·검색·관련 뉴스·내부
  API는 기록하지 않는다.
- 사용자·세션별 중복 제거, MySQL Snapshot, 과거 순위와 스케줄러는 Phase 4 완료를 막지
  않는 운영 개선 backlog다. 다음 단계는 Phase 5 통합 품질과 운영 준비다.
- Phase 5는 진행 중이다. Actuator health·info와 liveness·readiness probe를 같은
  애플리케이션 포트에 추가했다. Readiness는 MySQL과 Redis를 필수 의존성으로 사용하고,
  liveness와 readiness 모두 Naver API를 호출하지 않는다.
- 구조화 로깅, 커스텀 비즈니스 메트릭, DB 인덱스 최적화와 CI는 후속 Phase 5 작업이다.
- Phase 5 요청 추적을 구현했다. 모든 HTTP 응답은 안전한 `X-Request-Id`를 포함하고 동기
  요청 범위의 MDC와 구조화 완료·오류 로그가 같은 ID를 사용한다. 운영 기본 로그는 JSON,
  local은 requestId가 보이는 console pattern이다.
- 요청·응답 body, query string과 인증·쿠키·Provider 자격 증명은 기록하지 않는다.
  비동기 MDC 전파, 분산 추적, 커스텀 메트릭과 로그 수집 인프라는 후속 backlog다.
