# Waiting Registration JMeter Load Test

인기 매장에 웨이팅 등록 요청을 동시에 집중시켜 순번 중복, 응답 지연, 실패율을 확인하는 JMeter 테스트 계획이다.

## 대상 API

```http
POST /api/v1/waitings
Content-Type: application/json
X-User-Id: ${userId}
X-Role: USER
```

```json
{
  "storeId": "${storeId}",
  "peopleCount": 2,
  "requestMessage": "JMeter load test"
}
```

## 실행 전 준비

1. `waiting-service`를 실행한다.
2. 테스트에 사용할 매장 1개를 준비한다.
   - 기본 매장 ID: `49e778c5-522c-4cfd-86a9-57f5e9b7fab4`
3. 해당 매장의 `maxWaitingCount`를 요청 수보다 크게 설정한다.
   - 예: 100명 테스트면 `maxWaitingCount >= 100`
4. 이미 등록된 웨이팅이 있으면 결과 해석이 어려우므로 테스트 매장은 비어 있는 상태로 시작한다.
5. 기본 실행 스크립트는 매 실행마다 새 사용자 ID CSV를 생성한다.
   - 생성 위치: `jmeter/waiting/generated-users/`

## CLI 실행

권장 실행 방식은 스크립트 사용이다.

```bash
jmeter/waiting/run-waiting-load-test.sh 50 10 1
```

인자 순서:

```text
threads rampUp loops
```

예시:

```bash
jmeter/waiting/run-waiting-load-test.sh 100 10 1
jmeter/waiting/run-waiting-load-test.sh 300 30 1
```

실행이 끝나면 아래 파일이 생성된다.

```text
jmeter/waiting/generated-users/users-{threads}-{rampUp}-{loops}-{timestamp}.csv
jmeter/waiting/results/waiting-registration-{threads}-{rampUp}-{loops}.jtl
jmeter/waiting/results/html-{threads}-{rampUp}-{loops}/index.html
```

직접 `jmeter` 명령을 실행하려면 테스트 사용자 CSV를 준비한 뒤 아래처럼 실행한다.

```bash
jmeter -n \
  -t jmeter/waiting/waiting-registration-load-test.jmx \
  -Jhost=localhost \
  -Jport=8004 \
  -JstoreId=49e778c5-522c-4cfd-86a9-57f5e9b7fab4 \
  -JuserCsv=jmeter/waiting/users.csv \
  -Jthreads=50 \
  -JrampUp=10 \
  -Jloops=1 \
  -l jmeter/waiting/results/waiting-registration-50.jtl \
  -e \
  -o jmeter/waiting/results/html-50-10-1
```

API Gateway를 통해 실행할 경우 `port`와 필요하면 path를 JMX에서 조정한다.

## 단계별 부하 계획

| 단계 | threads | rampUp | loops | 목적 |
|---|---:|---:|---:|---|
| 1차 | 50 | 10 | 1 | 설정 검증 |
| 2차 | 100 | 10 | 1 | 기본 부하 |
| 3차 | 300 | 30 | 1 | 병목 관찰 |
| 4차 | 500 | 60 | 1 | 한계 관찰 |

처음부터 높은 수치로 시작하지 말고 50명부터 올린다.

## JMX에 포함된 검증

- HTTP 응답 코드는 `201 CREATED`여야 한다.
- 응답 JSON의 `$.data.waitingNumber`가 존재해야 한다.
- 테스트 실행 중 같은 `waitingNumber`가 두 번 나오면 Assertion 실패로 처리한다.
- 스크립트 실행 시 각 요청의 `X-User-Id`는 새로 생성된 CSV에서 한 줄씩 읽으므로, 이전 실행의 사용자 중복 등록과 섞이지 않는다.

## 캡처할 화면

1. CLI 실행 완료 화면
   - 실행 명령, `summary`, 결과 파일 경로가 보이게 캡처
2. HTML 리포트 `index.html`
   - Samples, Average, Min, Max, Error %, Throughput이 보이게 캡처
3. 필요하면 `.jtl` 일부
   - `responseCode=201`, `success=true`가 보이게 캡처
4. 필요하면 테스트 후 매장 웨이팅 목록 조회 결과
   - `waitingNumber` 중복 없음 확인용

## 결과 정리 예시

```text
JMeter로 동일 매장에 100명의 동시 웨이팅 등록 요청을 발생시켰다.
요청별 사용자 ID는 실행 시 생성한 CSV로 분리하여 동일 사용자 중복 등록 조건을 제거했다.
테스트 결과 Error %는 0%, 평균 응답 시간은 n ms였으며,
응답 waitingNumber 중복 Assertion 실패는 발생하지 않았다.
```
