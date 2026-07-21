# 후속 Backlog

## 범위

EconPulse 1.0.0의 백엔드 MVP와 Phase 5 운영 준비 범위는 완료됐다. 아래 항목은 완료 범위와
섞지 않고 별도 제품·운영 결정, 보안 설계와 성능 측정 후 진행한다.

## 제품 확장

- 뉴스별 연결 경제용어 조회
- 전체·기간별 뉴스 재매핑
- 뉴스 수집 후 자동 매핑 orchestration
- 사용자·세션별 인기 조회 중복 제거
- 과거 인기 순위 조회
- 대규모 contains 검색을 위한 prefix/FULLTEXT/전용 검색 대안 검토
- 큰 offset과 대규모 관련 뉴스의 cursor pagination 검토

## 운영 확장

- `PopularTermSnapshot` 저장 서비스와 보관 정책
- 뉴스 수집 스케줄러
- Spring Batch와 비동기 Job
- 실제 Naver credential smoke
- Prometheus Registry
- Grafana dashboard와 alert
- Docker image build·publish
- CD와 실제 서버·클라우드 배포
- Kubernetes manifest
- 운영 내부 API 인증과 네트워크 ACL
- Dependabot과 CodeQL
- 비동기 MDC 전파, 분산 추적과 외부 로그 수집
- 수동 클린 환경 GitHub Actions workflow
- 필요 시 JPA persistence entity와 순수 domain model 분리 검토

현재 저장소에는 위 기능의 동작을 암시하는 임시 설정이나 Secret을 추가하지 않는다.
