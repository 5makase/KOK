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
                //send()는 비동기 -> get()으로 브로커를 기다림
                kafkaTemplate.send(TOPIC, event.getStoreId().toString(), event.getPayload()).get();/*get() :브로커가 메세지를 받았는지 확인하는 메서드.
                                                                                                - 받으면 markPublished 실행
                                                                                                - 못받았으면 예외 발생.*/
                //확인된 후에만 PUBLISHED
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

}