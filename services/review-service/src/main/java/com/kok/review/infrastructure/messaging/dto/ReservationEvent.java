package com.kok.review.infrastructure.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)   // schemaVersion, producer 등 안 쓰는 필드 무시
public record ReservationEvent(
        UUID eventId,
        String eventType,
        Payload payload          // ← 중첩 구조 그대로 받기
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            UUID reservationId,
            UUID userId,
            UUID storeId,
            String storeName,
            LocalDateTime visitedAt
    ) {}
}