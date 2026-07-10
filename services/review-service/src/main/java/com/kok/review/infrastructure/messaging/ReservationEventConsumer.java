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
        if (!"RESERVATION_VISITED".equals(event.eventType())) {
            return;
        }

        var payload = event.payload();   // 중첩 payload 꺼내기

        if (event.eventId() == null || payload == null
                || payload.reservationId() == null || payload.userId() == null
                || payload.storeId() == null || payload.visitedAt() == null) {
            log.warn("필수 필드 누락된 이벤트 무시. event={}", event);
            return;
        }

        if (reviewEligibilityRepository.existsByEventId(event.eventId())) {
            log.info("중복 이벤트 무시. eventId={}", event.eventId());
            return;
        }

        ReviewEligibility reviewEligibility = ReviewEligibility.create(event);
        reviewEligibilityRepository.save(reviewEligibility);
        log.info("리뷰 작성 권한 적재 완료 reservationId={}", payload.reservationId());
    }
}
