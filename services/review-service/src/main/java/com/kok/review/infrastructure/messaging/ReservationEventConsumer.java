package com.kok.review.infrastructure.messaging;

import com.kok.review.domain.entity.ReviewEligibility;
import com.kok.review.infrastructure.messaging.dto.ReservationEvent;
import com.kok.review.infrastructure.persistence.ReviewEligibilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationEventConsumer {
    private final ReviewEligibilityRepository reviewEligibilityRepository;

    /**
     * 예약 방문 처리 되었을 경우, 이벤트를 받음.
     *
     * @param event 방문 처리되었을 받는 데이터
     */
    @KafkaListener(topics = "reservation.events.v1", groupId = "review-service-group")
    @Transactional
    public void consume(ReservationEvent event) {
        //방문 처리 안된 예약 필터링
        if (!"RESERVATION_VISITED".equals(event.eventType())) {
            return;
        }

        // 필수 필드 검증 — 하나라도 없으면 처리 불가한 메시지로 간주하고 버림
        if (event.eventId() == null || event.reservationId() == null
                || event.userId() == null || event.storeId() == null
                || event.visitedAt() == null) {
            log.warn("필수 필드 누락된 이벤트 무시. event={}", event);
            return;   // 재시도해도 고쳐질 게 아니므로 그냥 넘어감 (오프셋 커밋)
        }

        //방문 처리는 되었지만, 이미 권한 적재 테이블에 올라간 경우 필터링
        // = 이미 이 예약에는 리뷰를 작성했기 때문.(멱등처리)
        if (reviewEligibilityRepository.existsByEventId(event.eventId())) {
            log.info("중복 이벤트 무시. eventId={}", event.eventId());
            return;
        }

        //방문 처리 ok, 멱등처리 ok인 경우, 완전 처음 작성하는 리뷰로 간주
        ReviewEligibility reviewEligibility = ReviewEligibility.create(event);

        //권한 적재 테이블에 저장.
        reviewEligibilityRepository.save(reviewEligibility);
        log.info("리뷰 작성 권한 적재 완료 reservationId={}", event.reservationId());
    }
}
