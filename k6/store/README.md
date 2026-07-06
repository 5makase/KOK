# Store Service k6 Load Test

store-service API에 대한 k6 부하 테스트 스크립트 모음.  
결과는 Grafana의 `Store 랭킹 성능 모니터링` / `Store Service Overview` 대시보드(`docker/grafana/provisioning/dashboards/`)에서 실시간으로 확인한다.

## 테스트 목록

| 파일 | 대상 API | 실행 모델 | 목적 |
|---|---|---|---|
| `ranking-load-test-vu.js` | `GET /api/v1/stores/ranking` | VU | 캐싱 적용 전후 처리량·지연 비교 (베이스라인 리포트) |
| `ranking-load-test-rps.js` | `GET /api/v1/stores/ranking` | RPS | SA 설계 목표(피크 150 RPS) 검증 |
| `search-load-test-vu.js` | `GET /api/v1/stores` | VU | 캐싱 적용 전후 처리량·지연 비교 (베이스라인 리포트) |
| `search-load-test-rps.js` | `GET /api/v1/stores` | RPS | SA 설계 목표(피크 150 RPS) 검증 |

새 테스트를 추가하면 이 표에 한 줄 추가한다. (규칙은 아래 "새 테스트 추가 시" 참고)

## 실행 모델 (파일명 접미사)

| 접미사 | 실행기 | 설명                                                                                                  |
|---|---|-----------------------------------------------------------------------------------------------------|
| `-vu` | `ramping-vus` (closed-model) | 동시 사용자 수(VU) 기준. "동시 사용자 N명이 몰리면 어떻게 되나"를 본다. 응답이 빨라지면 실제 RPS도 같이 늘어나므로, 여기 찍히는 RPS는 목표치가 아니라 결과값이다 |
| `-rps` | `constant-arrival-rate` (open-model) | 초당 요청 수 고정. 응답시간과 무관하게 목표 RPS를 그대로 검증한다 - "N RPS에서 통과하는가"에 1:1로 대응                                  |

## 실행 전 준비

1. API Gateway를 포함한 전체 서비스가 기동돼 있어야 한다 (`docker-compose up`).
2. 로그인 후 발급받은 JWT를 `TOKEN` 환경변수로 전달한다.

## 실행

```bash
docker run --rm -i \
  -e TOKEN="<발급받은 JWT>" \
  grafana/k6 run - < k6/store/<파일명>
```

모든 스크립트가 동일한 실행 방식을 따른다. 예: `k6/store/ranking-load-test-vu.js`

## 부하 프로파일

- `-vu`: 150 VU까지 30초 램프업 → 1분 유지 → 30초 램프다운
- `-rps`: 초당 150건 고정으로 1분간 유지 (`constant-arrival-rate`)

## 임계값

- `http_req_duration p(95) < 300ms`
- `http_req_failed rate < 1%`

임계값 실패 시 k6가 non-zero exit code로 종료된다.

## 캡처할 화면

- k6 CLI 실행 완료 화면 : 실행 명령, `THRESHOLDS` 통과 여부, `TOTAL RESULTS`(p95·에러율·RPS)가 보이게
- Grafana 대시보드 : 테스트 실행 구간을 포함하는 시간 범위로, 관련 패널이 보이게
  - `-vu`: `Store 랭킹 성능 모니터링`의 p95·RPS·에러율·DB 커넥션
  - `-rps`: 위와 동일 대시보드에서 실제 RPS가 목표치(150)에 고정되는지
- 개선 전후 비교 시: 캐싱 적용 전/후 각각 위 두 화면을 따로 캡처해 나란히 비교

## 결과 정리 예시

### 단일 실행 (주로 `-rps`: 목표치 검증)

```text
k6로 <API>에 초당 <N>건(constant-arrival-rate) 부하를 <기간> 발생시켰다.
p95 응답시간 <n>ms, 에러율 <n>%로 SA 설계 목표(피크 <N> RPS)를 충족했다.
```

### Before / After 비교 (주로 `-vu`: 개선 전후 리포트)

개선 작업(캐싱 도입, 쿼리 최적화 등) 전후를 비교할 때는 아래 구조로 정리한다.(참고용 포맷)

```text
**Before (<브랜치/조건>)**
- p95: <n> (threshold `p95<300ms` <PASS|FAIL>)
- RPS: <n> req/s
- 에러율: <n>%
- <병목으로 지목된 지표> (예: DB 커넥션 N개 지속 점유)
- 처리량: <n> checks / <기간>

**After (<브랜치/조건>)**
- p95: <n>
- RPS: <n> req/s
- 에러율: <n>%
- <같은 지표의 변화>
- 처리량: <n> checks / <기간>

**개선 폭**

| 지표 | Before | After | 개선 |
|---|---|---|---|
| p95 | | | |
| RPS | | | |
| <병목 지표> | | | |
| 처리 요청 수 | | | |

**공식 비교에서 제외한 결과 (있다면)**
- 어떤 측정을 왜 제외했는지, 원인, 재측정 방법을 남긴다 (착시 결과·환경 이슈 등으로 재측정한 경우)

**산출물 체크리스트**
- [ ] Before/After k6 raw 결과 (터미널 캡처)
- [ ] Before/After Grafana 대시보드 스크린샷
- [ ] Before/After 비교표
- [ ] (있다면) 트러블슈팅 사이드노트
```

## 새 테스트 추가 시

1. 파일명: `<endpoint>-load-test-<vu|rps>.js` (예: `store-detail-load-test-vu.js`)
2. 위 "테스트 목록" 표에 한 줄 추가 (대상 API / 실행 모델 / 목적)
3. 임계값은 기존과 동일한 기준(`p(95)<300`, `rate<0.01`)을 기본으로 하되, 대상 API 특성상 다르면 표에 사유와 함께 명시
4. 새 지표를 추가로 봐야 하면 Grafana 대시보드 JSON도 같이 갱신 (`docker/grafana/provisioning/dashboards/`)
