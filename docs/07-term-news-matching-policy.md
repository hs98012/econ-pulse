# Term-News Matching Policy

## 1. 현재 구현 범위

`TermNewsMatcher`는 한 경제용어와 한 뉴스의 데이터만 받아 최종 후보 하나를 순수하게
계산한다. 매칭 대상은 뉴스 제목과 요약이며 본문과 URL은 사용하지 않는다. 매처는
DB 조회, JPA 엔티티, Repository, Spring Bean, HTTP Provider에 의존하지 않고
`TermNewsMappingService`를 호출하지 않는다.

순수 매처는 후보 계산만 담당한다. 별도의 `TermNewsAutoMappingService`가 최대 100개의
명시적 저장 뉴스 ID와 ACTIVE 용어를 DB에서 읽고 입력 모델로 변환해 후보를 기존 멱등
저장 서비스에 연결한다. 기본 비활성 내부 Controller가 최대 100개의 원본 뉴스 ID를
받아 이 흐름을 명시적으로 실행한다. 뉴스 수집 후 자동 호출, ID 없는 전체 재처리,
관련 뉴스 API와 스케줄러는 구현하지 않았다.

## 2. 입력과 출력

- `TermMatchTarget`: 양수 term ID, 비어 있지 않은 이름, null이 아닌 별칭 목록
- `NewsMatchContent`: 양수 news article ID, 비어 있지 않은 제목, 비어 있을 수 있는 요약
- `TermMatchCandidate`: term/article ID, `EXACT_NAME` 또는 `ALIAS`, confidence score,
  정규화된 matched text, `TITLE` 또는 `SUMMARY`

입력 모델은 생성 시 문자열을 한 번 정규화한다. 별칭은 정규화 기준으로 중복 제거하고,
빈 값과 정규화된 이름과 같은 값을 제거한 불변 목록으로 보관한다. 따라서 호출자가 원래
별칭 컬렉션을 바꾸어도 매칭 결과는 변하지 않는다.

## 3. 문자열 정규화

용어, 별칭, 제목과 요약은 공통 `TextNormalizer`의 다음 정책을 사용한다.

1. Unicode NFKC 정규화
2. 앞뒤 공백 제거
3. 연속 공백을 ASCII 공백 하나로 축약
4. `Locale.ROOT` 기준 영문 소문자화

문장부호는 삭제하지 않는다. 외부 Provider HTML 정제는 Adapter 경계의 책임이며 매처는
HTML 파서 역할을 하지 않는다. `TermNormalizer`와 `NewsTextNormalizer`도 같은 공통
구현에 위임해 기능별로 정규화 로직을 복제하지 않는다.

## 4. 일치와 경계 정책

- 정규화된 용어명이 제목 또는 요약에 있으면 `EXACT_NAME`이다.
- 용어명이 일치하지 않고 정규화된 별칭이 있으면 `ALIAS`다.
- 한글이 포함된 표현과 한글·영문·숫자 혼합 표현은 조사 결합을 허용하기 위해 부분
  문자열로 비교한다. 예를 들어 `기준금리`는 `기준금리가`에 일치한다.
- ASCII 영문·숫자와 공백으로만 구성된 표현은 표현 양끝이 ASCII 영숫자 내부가 아닌지
  확인한다. 따라서 `GDP`, `GDP는`, `2026 GDP 전망`은 일치하고 `myGDPvalue`는
  일치하지 않는다. 숫자 표현 `2026`도 더 긴 ASCII 숫자 내부에서는 일치하지 않는다.
- 문장부호는 ASCII 영숫자 토큰의 경계로 인정한다.

정규화 후 한 코드 포인트인 별칭은 언어와 관계없이 자동 매칭 대상에서 조용히 제외한다.
두 코드 포인트 이상 별칭은 위 경계 정책에 따라 검사한다. 용어명에는 이 짧은 별칭 필터를
적용하지 않는다.

## 5. 후보 선택과 점수

최종 후보는 다음 순서로 하나만 선택한다.

1. 제목 `EXACT_NAME`: `1.0000`
2. 요약 `EXACT_NAME`: `0.9000`
3. 제목 `ALIAS`: `0.8000`
4. 요약 `ALIAS`: `0.7000`

점수는 한곳에 선언한 scale 4 `BigDecimal` 상수이며 모두 `0.0000` 이상 `1.0000`
이하다. 같은 분류의 표현이 여러 개면 Unicode 코드 포인트 길이가 긴 표현을 먼저
선택하고, 길이도 같으면 정규화된 문자열 사전순으로 선택한다. 별칭 입력 순서나 Set 구현
순서에는 의존하지 않는다. 이름과 별칭이 모두 일치하면 항상 `EXACT_NAME`, 제목과
요약이 모두 같은 유형으로 일치하면 제목을 선택한다. 아무 표현도 없으면 빈
`Optional`을 반환한다.

## 6. 저장 데이터 자동 매핑 경계

- 명령은 null·빈 목록·0 이하 ID를 거부하고 중복 제거 후 뉴스 ID 오름차순으로 보관한다.
- 한 호출은 최대 100개의 고유 뉴스 ID만 처리하며 모든 ID가 DB에 존재해야 한다.
- ACTIVE 용어는 별칭을 EntityGraph로 함께 초기화해 N+1 조회를 피하고 ID 오름차순으로
  처리한다. 뉴스도 한 번의 ID 목록 쿼리로 읽고 ID 오름차순으로 처리한다.
- Application 계층은 저장된 normalized name과 normalized aliases를 사용해
  `TermMatchTarget`을 만들고 뉴스 제목·요약만 `NewsMatchContent`에 전달한다.
- 후보가 있을 때만 ID, match type, score로 `TermNewsMappingCommand`를 만들며 설명용
  matched text와 field는 저장하지 않는다.
- 결과의 요청 뉴스 수는 중복 제거된 고유 ID 수다. 처리 뉴스 수는 검증 후 실제 처리한
  뉴스 수, 활성 용어 수는 한 호출에서 평가한 용어 수다. 평가 조합 수는 실제 매처 호출
  수, 후보 수는 Optional이 존재한 수, 미일치 수는 Optional이 빈 수다.
- `evaluatedPairCount = matchedCandidateCount + unmatchedPairCount`이며
  `matchedCandidateCount = created + updated + skipped`다.

AutoMappingService는 전체 호출을 큰 트랜잭션으로 감싸지 않는다. 각 후보 저장은 Spring
proxy를 통해 기존 `TermNewsMappingService`의 트랜잭션을 재사용한다. 예상하지 못한 오류는
즉시 전파하고 부분 성공 결과는 반환하지 않는다. 다만 오류 전에 커밋된 개별 저장을 전체
호출 단위로 롤백하지는 않는다. `REQUIRES_NEW`와 parallel stream은 사용하지 않는다.

계산량은 `뉴스 수 × ACTIVE 용어 수`다. 초기 규모에서는 전체 활성 용어 순회를 허용하지만
뉴스 입력을 100건으로 제한한다. 데이터 증가 시 후보 용어 사전 필터 또는 검색 인덱스를
검토하고, 전체 재처리는 반드시 페이징·청크 단위로 호출해야 한다.

내부 rebuild API의 원본 배열은 100개까지 허용하며 중복은 Command에서 제거한다. 따라서
결과의 요청 수는 고유 ID 수다. 기능 토글은 인증이 아니며 뉴스 동기화와 독립적이다.

## 7. 관련 뉴스 공개 조회

ACTIVE 용어의 저장된 매핑은 `publishedAt DESC`, 같은 시각은 뉴스 ID DESC로 DB에서
직접 페이징한다. 응답에는 뉴스 공개 메타데이터와 `EXACT_NAME`/`ALIAS`, confidence
score를 포함하지만 matched text/field/at과 매핑 ID는 포함하지 않는다. 관련 매핑이
없으면 빈 페이지이며 미존재·INACTIVE 용어는 공개 상세 정책과 동일하게 404다. 조회는
순수 매처나 자동 매핑 서비스를 다시 실행하지 않고 저장된 결과만 읽는다.
