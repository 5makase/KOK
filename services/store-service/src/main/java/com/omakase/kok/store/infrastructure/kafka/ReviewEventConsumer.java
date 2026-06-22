package com.omakase.kok.store.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.store.application.StoreRatingService;
import com.omakase.kok.store.infrastructure.kafka.event.ReviewEvent;
import com.omakase.kok.store.infrastructure.kafka.event.ReviewEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewEventConsumer {

    private final StoreRatingService storeRatingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "review.events.v1", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) {
        if (message == null || message.isBlank()) {
            log.warn("review.events.v1 null/blank 메시지 무시");
            return;
        }
        try {
            ReviewEvent event = objectMapper.readValue(message, ReviewEvent.class);

            ReviewEventType eventType = ReviewEventType.from(event.getEventType())
                    .orElse(null);
            if (eventType == null) {
                log.warn("처리할 수 없는 eventType. eventType={}, eventId={}", event.getEventType(), event.getEventId());
                return;
            }

            // 필수 필드 검증 - 하나라도 없으면 처리 불가한 메시지로 간주하고 버림
            ReviewEvent.Payload payload = event.getPayload();
            if (event.getSchemaVersion() == null || payload == null || payload.getStoreId() == null || payload.getAverageRating() == null || payload.getReviewCount() == null) {
                log.warn("필수 필드 누락된 이벤트 무시. eventId={}", event.getEventId());
                return;
            }
            if (payload.getReviewCount() < 0) {
                log.warn("유효하지 않은 reviewCount 이벤트 무시. eventId={}, storeId={}, reviewCount={}",
                        event.getEventId(), payload.getStoreId(), payload.getReviewCount());
                return;
            }

            storeRatingService.updateRating(payload.getStoreId(), payload.getAverageRating(), payload.getReviewCount());
            log.info("매장 평점 갱신 완료. storeId={}, averageRating={}, reviewCount={}",
                    payload.getStoreId(), payload.getAverageRating(), payload.getReviewCount());
        } catch (JsonProcessingException e) {
            // 재처리해도 고쳐지지 않는 poison pill - 오프셋 커밋하고 넘어감
            // raw payload는 개인정보 노출 방지를 위해 로그에서 제외
            log.warn("review.events.v1 역직렬화 실패 — 메시지 버림 (raw payload omitted)", e);
        } catch (Exception e) {
            log.error("review.events.v1 처리 실패. error={}", e.getMessage(), e);
            throw new RuntimeException("review.events.v1 처리 실패 — Kafka 재처리 유도", e);
        }
    }
}
