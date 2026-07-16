# 실시간 인기 용어 정책

## 범위

Phase 4 첫 단계는 Redis 실시간 일간 점수의 독립 저장 경계다. 공개 API, 기존 경제용어
검색 흐름의 자동 기록, 경제용어 이름 조회, MySQL Snapshot과 스케줄러는 포함하지 않는다.

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

Redis Sorted Set은 실시간 점수만 담당한다. `PopularTermSnapshot`은 향후 특정 시점의
순위를 MySQL에 영속 보관하는 별도 책임이며 현재 서비스와 Adapter는 Snapshot
Repository에 의존하지 않는다. 미존재 용어 ID 유입·정리, Redis 만료 전 백업,
Snapshot 저장과 스케줄러는 후속 작업이다.
