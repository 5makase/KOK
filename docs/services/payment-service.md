# Payment Service

**결제 관리** (내부 서비스 간 통신용 API)
- 결제 생성, 환불(전액/부분), 만료 처리, 예약ID 기준 결제 조회

**상태**
- `PENDING → PAID → REFUNDED / PARTIALLY_REFUNDED`, `PENDING → EXPIRED`, `PENDING → CANCELLED`
- 상태 전이 검증을 엔티티 메서드 내부에서 처리, 위반 시 예외 발생

**중복결제방지**
- `reservationId` 기준 기존 결제 존재 여부 확인 후 중복 생성 차단 (`PAYMENT_ALREADY_EXISTS`)
- 환불 금액이 0 이하이거나 원 결제금액을 초과하면 예외 처리

**특징 / 한계**
- 실제 PG사(토스페이먼츠 등) 연동은 미구현 — `paymentMethod`는 문자열 필드로만 저장, 승인/결제창 연동 로직 없음
- **Kafka 미구현** — 의존성/설정만 존재하고 Producer/Consumer 코드는 없음
