# Waiting Synthetic JMeter Load Test

금요일 저녁 피크 시간대에 인기 매장으로 웨이팅 요청이 몰리는 상황을 Synthetic Data 기반으로 재현한다.

## LLM 프롬프트 원문

```text
레스토랑 웨이팅 서비스의 금요일 저녁 피크 트래픽 시나리오를 만들고 싶어.

총 300명의 가상 사용자를 CSV로 생성해줘.
시나리오는 다음 조건을 따른다.

- 시간대는 금요일 18:00~19:00 저녁 피크
- 전체 사용자 중 70%는 특정 인기 매장 storeId=49e778c5-522c-4cfd-86a9-57f5e9b7fab4에 웨이팅 등록을 시도
- 20%는 일반 매장 4개에 분산되어 웨이팅 등록
- 10%는 조회만 하고 등록하지 않음
- 각 사용자는 서로 다른 userId를 가진다
- arrivalDelayMs는 사용자가 테스트 시작 후 몇 ms 뒤 행동을 시작하는지 의미한다
- 인기 매장 사용자는 0~3000ms 사이에 집중되도록 생성
- 일반 매장 사용자는 0~30000ms 사이에 분산
- peopleCount는 1~6 사이
- requestMessage는 일부 사용자만 작성
- pollCount는 웨이팅 등록 후 내 웨이팅 조회를 반복하는 횟수
- pollIntervalMs는 조회 간격이다

CSV 컬럼은 다음과 같다.
userId,role,storeId,scenarioType,arrivalDelayMs,peopleCount,requestMessage,pollCount,pollIntervalMs

scenarioType은 POPULAR_REGISTER, NORMAL_REGISTER, VIEW_ONLY 중 하나로 생성해줘.
```

## Synthetic Data 구성

| 유형 | 비율 | 사용자 수 | 행동 |
|---|---:|---:|---|
| POPULAR_REGISTER | 70% | 210 | 인기 매장 등록 후 내 웨이팅 조회 반복 |
| NORMAL_REGISTER | 20% | 60 | 일반 매장 4개에 분산 등록 후 내 웨이팅 조회 |
| VIEW_ONLY | 10% | 30 | 등록 없이 내 웨이팅 조회 반복 |

기본 CSV는 인기 매장 1개와 일반 매장 4개를 사용한다.

| 구분 | storeId | ownerId |
|---|---|---|
| 인기 매장 | 49e778c5-522c-4cfd-86a9-57f5e9b7fab4 | c5557b4b-014e-4b38-b889-8d60e61052d5 |
| 일반 매장 | cc000009-0000-0000-0000-000000000009 | bbbbbbbb-0000-0000-0000-000000000003 |
| 일반 매장 | cc000002-0000-0000-0000-000000000002 | aaaaaaaa-0000-0000-0000-000000000002 |
| 일반 매장 | cc000003-0000-0000-0000-000000000003 | aaaaaaaa-0000-0000-0000-000000000002 |
| 일반 매장 | cc000017-0000-0000-0000-000000000017 | bbbbbbbb-0000-0000-0000-000000000003 |

다른 매장으로 실행해야 하면 환경변수로 재생성한다.

```bash
POPULAR_STORE_ID=49e778c5-522c-4cfd-86a9-57f5e9b7fab4 \
NORMAL_STORE_IDS=store-id-1,store-id-2,store-id-3,store-id-4 \
jmeter/waiting/generate-synthetic-waiting-users.py
```

생성 파일:

```text
jmeter/waiting/synthetic-data/friday-peak-300.csv
```

## 실행

```bash
jmeter/waiting/generate-synthetic-waiting-users.py
jmeter/waiting/run-synthetic-waiting-load-test.sh 300 30 1
```

순간 집중도를 높이려면 ramp-up을 줄인다.

```bash
jmeter/waiting/run-synthetic-waiting-load-test.sh 300 10 1
jmeter/waiting/run-synthetic-waiting-load-test.sh 300 1 1
```

## 확인 지표

- `POST /api/v1/waitings`: 201, 409, timeout 비율
- `GET /api/v1/waitings/me`: 조회 지연 여부
- 인기 매장 등록 요청과 일반 매장 등록 요청의 응답 시간 차이
- 전체 평균, median, p90, p95, p99
- SocketTimeoutException 발생 지점

## 결과 해석 가이드

| 현상 | 원인 추측 | 개선 방향 |
|---|---|---|
| 인기 매장 등록 API에서 timeout 증가 | 특정 storeId의 Redis queue/sequence key에 요청 집중 | Gateway rate limit 또는 사전 대기열 검토 |
| 조회 API는 안정적이나 등록 API만 지연 | Redis Lua, DB 저장, Outbox 저장이 등록 경로에 집중 | 등록 경로 상세 계측, Outbox 저장 비용 점검 |
| timeout 요청이 DB에 뒤늦게 등록됨 | 클라이언트 timeout과 서버 처리 완료 시점 불일치 | timeout 후 내 웨이팅 조회로 등록 결과 확인 |
| 일반 매장은 안정적이고 인기 매장만 지연 | Hot Key 패턴 | 인기 매장 전용 보호 정책 검토 |
