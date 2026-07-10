# Notification Service

**알림 관리**
- 알림 목록 조회(페이징), 미읽음 개수 조회(Redis 캐시), 단건/전체 읽음 처리, 알림 삭제(soft delete)

**Slack 발송**
- Slack DM 단일 채널만 지원(이메일/SMS/푸시 없음)
- user-service에서 `slackId` 조회 → Slack userId 변환 → DM 채널 오픈 → 메시지 발송
- 발송 성공/실패/스킵을 건별로 기록, 실패 건은 스케줄러가 주기적으로 재시도(분산락 사용)

**Kafka 연동**
- `reservation.events.v1`, `waiting.events.v1` 구독 → 전체 이벤트를 알림으로 변환
- 이벤트의 `producer` 필드로 참조 타입/도메인 ID를 매핑, eventType을 알림 타입으로 변환
- eventId + 업무 키(참조ID+타입+유저) 이중 Redis 멱등성 체크로 중복 알림 방지
- 수동 ack 사용: 복구 불가능한 오류(역직렬화 실패 등)는 즉시 ack 후 로그만 남기고, 복구 가능한 오류(DB 장애 등)는 ack 후 재시도 스케줄러에 위임 — 무한 재처리 루프 방지

> 참고: `NotificationType`에는 결제(완료/환불/만료임박), 계정(가입환영/점주승인·거절) 등도 정의되어 있으나, payment-service·user-service의 Kafka 미구현으로 아직 실제로 발행되지 않는 "설계된 미연결 흐름"입니다.
