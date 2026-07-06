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

| 접미사 | 실행기 | 설명 |
|---|---|---|
| `-vu` | `ramping-vus` (closed-model) | 동시 사용자 수(VU) 기준. "동시 사용자 N명이 몰리면 어떻게 되나"를 본다. 응답이 빨라지면 실제 RPS도 같이 늘어나므로, 여기 찍히는 RPS는 목표치가 아니라 결과값이다 |
| `-rps` | `constant-arrival-rate` (open-model) | 초당 요청 수 고정. 응답시간과 무관하게 목표 RPS를 그대로 검증한다 — "N RPS에서 통과하는가"에 1:1로 대응 |

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

## 새 테스트 추가 시

1. 파일명: `<endpoint>-load-test-<vu|rps>.js` (예: `store-detail-load-test-vu.js`)
2. 위 "테스트 목록" 표에 한 줄 추가 (대상 API / 실행 모델 / 목적)
3. 임계값은 기존과 동일한 기준(`p(95)<300`, `rate<0.01`)을 기본으로 하되, 대상 API 특성상 다르면 표에 사유와 함께 명시
4. 새 지표를 추가로 봐야 하면 Grafana 대시보드 JSON도 같이 갱신 (`docker/grafana/provisioning/dashboards/`)
