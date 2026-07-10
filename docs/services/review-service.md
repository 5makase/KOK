# Review Service

**리뷰 관리**
- 리뷰 작성/수정/삭제(soft delete), 목록 조회(매장별, 정렬·포토리뷰 필터·페이징), 내 리뷰·상세 조회
- 리뷰 신고, 신고 승인/거부(관리자), 사장님 답글 작성/수정/삭제

**작성 권한 검증**
- `ReviewEligibility` 원장으로 예약자 본인·매장 일치·미사용 여부 확인 후에만 작성 허용 (방문 완료자만 작성 가능)
- `reservation_id` UNIQUE 제약으로 예약당 리뷰 1개 강제

**평점 집계**
- 매장별 `ReviewRatingSummary`에서 합계/개수 증분 방식으로 관리(작성 시 add, 삭제 시 subtract, 수정 시 replace)
- 낙관적 락 충돌 시 최대 3회 재시도

**신고/답글 정책**
- 자기 리뷰 신고 금지, 중복 신고 금지(신고자+리뷰 UNIQUE)
- 답글은 매장 실소유주(OWNER)만 작성 가능, 리뷰당 답글 1개
- MASTER가 신고 승인 시 리뷰 블라인드 처리 + 신고자 결과 알림 발행

**Kafka 연동**
- `reservation.events.v1` 구독 → `RESERVATION_VISITED` 이벤트로 리뷰 작성 권한 적재
- Outbox 패턴으로 리뷰 생성/수정/삭제/답글/신고결과를 `review.events.v1`에 발행
