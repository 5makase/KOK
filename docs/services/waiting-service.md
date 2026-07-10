# Waiting Service

**웨이팅 관리**
- 웨이팅 등록/취소, 내 웨이팅 목록·상세 조회
- 매장별 웨이팅 현황 조회(사장님/마스터), 다음 순번 호출, 입장 완료 처리, 미입장(No-Show) 처리
- 매장별 웨이팅 설정(대기 허용 여부, 최대 인원, 호출 제한시간 등) 관리

**상태 & 순번 관리**
- 상태: `WAITING → CALLED → ENTERED / CANCELLED / NO_SHOW`
- 순번은 Redis Sorted Set 기반(매장·날짜별 큐), Lua 스크립트로 등록/제거/순번조회 원자적 처리
- DB 데이터 기반 큐 복구(restore) 기능

**실시간성 처리**
- WebSocket/SSE 미사용, 클라이언트 폴링 방식(순번 조회 API)
- 다음 순번 호출은 Redisson 분산락으로 동시성 제어
- 호출 만료된 웨이팅은 스케줄러가 크론 배치로 자동 노쇼 처리

**Kafka 연동**
- Outbox 패턴으로 등록/호출/입장/취소/노쇼 이벤트를 `waiting.events.v1`에 발행
- `store.events.v1` 구독 → 매장 생성 시 기본 웨이팅 설정 자동 초기화
- 재시도 후 실패 시 `DeadLetterPublishingRecoverer`로 DLT(`store.events.v1.DLT`) 전송
