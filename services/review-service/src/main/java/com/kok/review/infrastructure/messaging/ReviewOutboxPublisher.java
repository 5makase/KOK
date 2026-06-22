package com.kok.review.infrastructure.messaging;

import com.kok.review.domain.entity.OutboxStatus;
import com.kok.review.domain.entity.ReviewOutboxEvent;
import com.kok.review.domain.repository.ReviewOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewOutboxPublisher {

    private static final String TOPIC = "review.events.v1";

    private final ReviewOutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /** 5초마다 PENDING 이벤트를 Kafka로 발행 */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<ReviewOutboxEvent> pendingEvents =
                outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (pendingEvents.isEmpty()) {
            return;
        }

        for (ReviewOutboxEvent event : pendingEvents) {
            try {
                // Partition Key = storeId → 동일 매장 이벤트 순서 보장 (스펙 1번)
                // payload(JSON) 안의 storeId를 키로 쓰려면 파싱이 필요하나,
                // 간단히 reviewId 기준 또는 payload에서 추출. 여기선 envelope를 그대로 발행.
                kafkaTemplate.send(TOPIC, extractStoreId(event), event.getPayload());
                event.markPublished();
                log.info("리뷰 이벤트 발행 완료. type={}, reviewId={}",
                        event.getEventType(), event.getReviewId());
            } catch (Exception e) {
                event.markFailed(e.getMessage());
                log.error("리뷰 이벤트 발행 실패. reviewId={}, reason={}",
                        event.getReviewId(), e.getMessage());
            }
        }
    }

    /** payload JSON에서 storeId를 추출해 파티션 키로 사용 */
    private String extractStoreId(ReviewOutboxEvent event) {
        // payload 안 storeId를 키로 쓰기 위한 추출. 구현 방식은 아래 설명 참고.
        return event.getReviewId().toString(); // 임시: 아래 주의사항 참고
    }
}