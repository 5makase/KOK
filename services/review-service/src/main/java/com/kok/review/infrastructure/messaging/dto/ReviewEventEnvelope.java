package com.kok.review.infrastructure.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** Kafka로 발행되는 공통 봉투(Envelope) */
public record ReviewEventEnvelope(
        UUID eventId,
        String eventType,
        int schemaVersion,
        LocalDateTime occurredAt,
        String producer,
        ReviewEventPayload payload
) {
    public static ReviewEventEnvelope of(String eventType, ReviewEventPayload payload) {
        return new ReviewEventEnvelope(
                UUID.randomUUID(),
                eventType,
                1,                       // schemaVersion
                LocalDateTime.now(),
                "review-service",
                payload
        );
    }
}