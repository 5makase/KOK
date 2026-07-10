package com.kok.review.infrastructure.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.UUID;

//예약 서비스에서 방문 처리시, 카프카로 받을 데이터 형식 정의
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