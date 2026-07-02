package com.kok.review.infrastructure.messaging.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

// 카프카로 데이터를 보낼 때 사용되는 전체 틀
public record ReviewEventEnvelope(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String producer,
        ReviewEventPayloadType payload
) {
    //ReviewEventEnvelope 생성
    public static ReviewEventEnvelope of(String eventType, ReviewEventPayloadType payload) {
        return new ReviewEventEnvelope(
                UUID.randomUUID(),
                eventType,
                1,
                Instant.now(),       // LocalDateTime.now() → Instant.now()
                "review-service",
                payload
        );
    }
}