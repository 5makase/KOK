package com.kok.review.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kok.review.domain.entity.ReviewOutboxEvent;
import com.kok.review.domain.repository.ReviewOutboxEventRepository;
import com.kok.review.infrastructure.messaging.dto.ReviewEventEnvelope;
import com.kok.review.infrastructure.messaging.dto.ReviewEventPayloadType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReviewOutboxAppender {
    private final ReviewOutboxEventRepository reviewOutboxEventRepository;
    private final ObjectMapper objectMapper;


    public void append(UUID reviewId, String eventType, UUID storeId, ReviewEventPayloadType payload) {
        ReviewEventEnvelope envelope = ReviewEventEnvelope.of(eventType, payload);
        String json = serialize(envelope);
        reviewOutboxEventRepository.save(ReviewOutboxEvent.create(reviewId,eventType,json,storeId));
    }

    private String serialize(ReviewEventEnvelope envelope) {
        try{
            return objectMapper.writeValueAsString(envelope);
        }catch (JsonProcessingException e) {
            throw new IllegalStateException("이벤트 직렬화 실패", e);
        }
    }
}
