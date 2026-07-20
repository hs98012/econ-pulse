# 실시간 인기 용어 정책

## 범위

Phase 4 현재 범위는 Redis 실시간 일간 점수의 저장 경계, Redis 인기 ID를 ACTIVE MySQL
용어 정보에 결합하는 Application 조회, UTC 오늘의 공개 API와 공개 상세 조회 성공 기록이다.
MySQL Snapshot, 과거 조회와 스케줄러는 포함하지 않는다.

## 저장 계약

- 자료구조: Redis Sorted Set
- key: `econpulse:popular-terms:{UTC yyyy-MM-dd}`
- member: 양수 `economicTermId`의 10진 문자열
- score: `ZINCRBY`로 증가하는 검색 횟수
- TTL: 각 증가 성공 후 7일로 갱신

Application의 score는 `long`이다. Adapter는 Redis `Double`이 null, 비유한 값, 음수,
소수 또는 IEEE-754 최대 안전 정수 `9,007,199,254,740,991` 초과이면
`INVALID_DATA`로 거부한다. member가 양수 long이 아니어도 같은 손상 상태다.

## 날짜와 순위

기록 날짜는 주입된 UTC `Clock`으로 계산한다. 조회는 명시적인 `LocalDate`와 1~100
limit을 요구한다. Redis에서는 필요한 limit만 `ZREVRANGE WITHSCORES`로 조회한다.
가져온 범위 안에서 `score DESC, economicTermId ASC`로 재정렬하고 rank는 1부터
부여한다. 이는 선택지 B이며 limit 경계 밖의 같은 score 후보까지 전역 tie-break를
보장하지 않는다.

## 장애와 책임 분리

Spring Data Redis 접근 오류는 연결 정보나 명령을 노출하지 않는
`PopularTermStoreException(UNAVAILABLE)`으로 변환한다. TTL 실패도 성공으로 숨기지
않는다. 향후 HTTP 계층은 이를 503 또는 별도 fallback 정책으로 연결할 수 있다.

Redis Sorted Set은 실시간 점수만 담당한다. 공개 API의 Redis 장애는
`503 POPULAR_TERM_STORE_UNAVAILABLE`로 변환하고 빈 배열이나 Snapshot으로 대체하지 않는다.
`PopularTermSnapshot`은 향후 특정 시점의
순위를 MySQL에 영속 보관하는 별도 책임이며 현재 서비스와 Adapter는 Snapshot
Repository에 의존하지 않는다. 미존재 용어 ID 유입·정리, Redis 만료 전 백업,
Snapshot 저장과 스케줄러는 후속 작업이다.

## 경제용어 정보 결합

`PopularTermQueryService`는 Redis에서 상위 limit개의 ID·score·순위를 먼저 조회한다.
결과가 비어 있으면 MySQL을 조회하지 않는다. ID가 있으면 ACTIVE 용어만 단일 `IN` 쿼리로
가져오고 aliases는 초기화하지 않는다. MySQL 반환 순서와 무관하게 Redis 순서를 기준으로
이름과 정의를 결합한다.

Redis에만 존재하거나 INACTIVE인 ID는 Redis에서 삭제하지 않고 조회 결과에서 제외한다.
포함된 결과만 기준으로 rank를 1부터 다시 부여하며 score와 동점 순서는 Store 결과를
유지한다. 선택지 A를 사용해 제외된 수만큼 추가 후보를 조회하지 않으므로 최종 결과가
요청 limit보다 적을 수 있다. Redis 장애는 기존 예외를 그대로 전달하고 Snapshot이나
MySQL 전체 목록으로 대체하지 않는다.

## 공개 API

`GET /api/v1/terms/popular?limit=10`은 Query Service가 주입된 UTC `Clock`으로 오늘을
계산한다. Controller는 날짜를 계산하거나 순서를 다시 정하지 않으며 Query Service에만
의존한다. limit 기본값은 10, 범위는 1~100이고 결과는 page wrapper 없는 배열이다.
미존재·INACTIVE 용어 제외 후 rank는 1부터 다시 계산한다. 인기 순위 조회 자체는 점수를
기록하지 않으며 Snapshot 저장·fallback·과거 조회도 수행하지 않는다.

## 상세 조회 성공 기록

현재 score의 의미는 검색어 제출 횟수가 아니라 ACTIVE 경제용어 공개 상세 조회 성공
횟수다. `GET /api/v1/terms/{termId}`에서 상세 Application 결과를 만든 다음 요청마다
`RecordTermSearchCommand`를 한 번 실행한다. 이름은 기존 계약 안정성을 위해 유지한다.
사용자·세션·IP 중복 제거는 없다.

0 이하·문자열 ID, 미존재·INACTIVE 용어와 상세 결과 생성 실패는 기록하지 않는다.
목록·검색 `GET /api/v1/terms`, 관련 뉴스, 인기 순위 자체, 뉴스 조회와 내부 API도
기록하지 않는다.

기록의 `UNAVAILABLE`은 비민감 용어 ID만 포함한 warning 후 fail-open한다. 상세 API는
200과 기존 DTO를 반환한다. 저장소 손상과 예상하지 못한 RuntimeException은 숨기지 않으며
재시도나 MySQL fallback은 없다. 인기 순위 조회의 Redis 장애는 기존 503을 유지한다.
MySQL 읽기와 Redis 기록은 XA·Redis transaction으로 묶지 않으며 동시 증가는 기존
`ZINCRBY` 원자성에 맡긴다.
