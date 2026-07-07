# API Gateway k6 Load Test

API Gateway 자체(JWT 검증 + 라우팅)의 처리량을 측정하는 k6 부하 테스트 스크립트 모음.
결과는 Grafana의 Gateway 관련 대시보드(`docker/grafana/provisioning/dashboards/`)에서 실시간으로 확인한다.

## 왜 별도 폴더가 있는가

Gateway는 자체 비즈니스 로직(REST 컨트롤러)이 없어서 "Gateway만" 호출하는 API가 없다.
대신 인증이 필요한 API 중 가장 가벼운 `GET /api/v1/users/me`(PK 단건 조회)를 타겟으로 잡아서,
Gateway가 매 요청마다 수행하는 JWT 파싱/검증 + 라우팅 오버헤드를 최대한 순수하게 측정한다.
백엔드(user-service) 쪽 처리는 매우 가벼운 조회라, 여기서 나오는 지연시간 대부분은 Gateway 구간으로 볼 수 있다.

## 테스트 목록

| 파일 | 대상 API | 실행 모델 | 목적 |
|---|---|---|---|
| `protected-route-load-test-vu.js` | `GET /api/v1/users/me` (Gateway 경유) | VU | JWT 검증+라우팅 처리량·지연 확인 |
| `protected-route-load-test-rps.js` | `GET /api/v1/users/me` (Gateway 경유) | RPS | SA 설계 목표(피크 150 RPS) 검증 |

새 테스트를 추가하면 이 표에 한 줄 추가한다. (규칙은 `k6/README.md`의 "새 서비스 테스트 추가 시" 참고)

## 실행 모델 (파일명 접미사)

| 접미사 | 실행기 | 설명 |
|---|---|---|
| `-vu` | `ramping-vus` (closed-model) | 동시 사용자 수(VU) 기준 |
| `-rps` | `constant-arrival-rate` (open-model) | 초당 요청 수 고정, 목표 RPS 통과 여부를 그대로 검증 |

## 실행 전 준비

1. API Gateway를 포함한 전체 서비스가 기동돼 있어야 한다 (`docker-compose up`).
2. 로그인 후 발급받은 JWT를 `TOKEN` 환경변수로 전달한다.
3. (선택) 기본값은 `http://host.docker.internal:8000`(로컬 API Gateway)이다. 다른 환경을 대상으로 하려면 `BASE_URL` 환경변수로 덮어쓴다.

## 실행

```bash
docker run --rm -i \
  -e TOKEN="<발급받은 JWT>" \
  grafana/k6 run - < k6/gateway/protected-route-load-test-vu.js
```

다른 환경을 대상으로 하려면 `BASE_URL`을 추가로 전달한다.

```bash
docker run --rm -i \
  -e TOKEN="<발급받은 JWT>" \
  -e BASE_URL="https://staging.example.com" \
  grafana/k6 run - < k6/gateway/protected-route-load-test-rps.js
```

## 부하 프로파일

- `-vu`: 150 VU까지 30초 램프업 → 1분 유지 → 30초 램프다운
- `-rps`: 초당 150건 고정으로 1분간 유지 (`constant-arrival-rate`)

Gateway는 SA 설계상 "전체 진입점"(Auto Scaling Max=4)이라 store-service와 동일한 150 목표치를 사용한다.

## 임계값

- `http_req_duration p(95) < 300ms`
- `http_req_failed rate < 1%`

임계값 실패 시 k6가 non-zero exit code로 종료된다.

## 캡처할 화면

- k6 CLI 실행 완료 화면 : 실행 명령, `THRESHOLDS` 통과 여부, `TOTAL RESULTS`(p95·에러율·RPS)가 보이게
- Grafana 대시보드 : 테스트 실행 구간을 포함하는 시간 범위로, Gateway CPU·응답시간·에러율 패널이 보이게

## 새 테스트 추가 시

`k6/README.md`의 공통 규칙을 따른다. Gateway 자체 API가 아니라 특정 서비스로 라우팅되는 API를 테스트하는 것이므로,
파일명/설명에 실제로 어떤 백엔드 서비스로 라우팅되는지 명시한다.