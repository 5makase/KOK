# User Service k6 Load Test

user-service API에 대한 k6 부하 테스트 스크립트 모음.
결과는 Grafana의 User Service 관련 대시보드(`docker/grafana/provisioning/dashboards/`)에서 실시간으로 확인한다.

## 테스트 목록

| 파일 | 대상 API | 실행 모델 | 목적 |
|---|---|---|---|
| `login-load-test-vu.js` | `POST /api/v1/auth/login` | VU | 로그인 순간 동시 요청(오픈 직후 등)에 대한 처리량·지연 확인 |
| `login-load-test-rps.js` | `POST /api/v1/auth/login` | RPS | 보수적 목표치(10 RPS)에서 통과 여부 검증 |

새 테스트를 추가하면 이 표에 한 줄 추가한다. (규칙은 `k6/README.md`의 "새 서비스 테스트 추가 시" 참고)

## 왜 store-service와 목표치가 다른가

- SA 기획서의 ECS Auto Scaling 설계상 User Service는 `Min 1 / Max 1`로, 명시적으로 저트래픽 서비스로 분류되어 있다.
  store-service 등 메인 도메인(150 RPS 목표)과 같은 기준을 적용하지 않는다.
- 로그인은 `BCryptPasswordEncoder`(기본 strength=10) 검증을 포함해 CPU 바운드 작업이다. 인스턴스 1대로 얼마나 버티는지가
  핵심 관심사라, VU/RPS 목표치를 낮게 잡고 대신 임계값(p95)도 store-service보다 여유 있게(500ms) 설정했다.

## 실행 모델 (파일명 접미사)

| 접미사 | 실행기 | 설명 |
|---|---|---|
| `-vu` | `ramping-vus` (closed-model) | 동시 사용자 수(VU) 기준. 로그인이 몰리는 순간을 시뮬레이션 |
| `-rps` | `constant-arrival-rate` (open-model) | 초당 요청 수 고정. "10 RPS에서 통과하는가"를 그대로 검증 |

## 실행 전 준비

1. API Gateway를 포함한 전체 서비스가 기동돼 있어야 한다 (`docker-compose up`).
2. 부하테스트용 계정을 미리 회원가입해둔다. (`TEST_USERNAME`, `TEST_PASSWORD` 환경변수로 전달, 기본값은 `loadtest_user` / `loadtest_password!1`)
3. (선택) 기본값은 `http://host.docker.internal:8000`(로컬 API Gateway)이다. 다른 환경을 대상으로 하려면 `BASE_URL` 환경변수로 덮어쓴다.

## 실행

```bash
docker run --rm -i \
  -e TEST_USERNAME="loadtest_user" \
  -e TEST_PASSWORD="loadtest_password!1" \
  grafana/k6 run - < k6/user/login-load-test-vu.js
```

다른 환경을 대상으로 하려면 `BASE_URL`을 추가로 전달한다.

```bash
docker run --rm -i \
  -e TEST_USERNAME="loadtest_user" \
  -e TEST_PASSWORD="loadtest_password!1" \
  -e BASE_URL="https://staging.example.com" \
  grafana/k6 run - < k6/user/login-load-test-rps.js
```

## 부하 프로파일

- `-vu`: 30 VU까지 20초 램프업 → 40초 유지 → 10초 램프다운
- `-rps`: 초당 10건 고정으로 1분간 유지 (`constant-arrival-rate`)

## 임계값

- `http_req_duration p(95) < 500ms` (bcrypt 검증 비용 반영, store-service보다 여유 있게 설정)
- `http_req_failed rate < 1%`

임계값 실패 시 k6가 non-zero exit code로 종료된다.

## 캡처할 화면

- k6 CLI 실행 완료 화면 : 실행 명령, `THRESHOLDS` 통과 여부, `TOTAL RESULTS`(p95·에러율·RPS)가 보이게
- Grafana 대시보드 : 테스트 실행 구간을 포함하는 시간 범위로, User Service CPU/응답시간 패널이 보이게

## 새 테스트 추가 시

`k6/README.md`의 공통 규칙을 따른다. 대상이 회원가입/JWT 재발급처럼 인증이 필요 없는 API라면 로그인과 동일하게
bcrypt 등 CPU 바운드 여부를 먼저 확인하고 목표치를 정한다.