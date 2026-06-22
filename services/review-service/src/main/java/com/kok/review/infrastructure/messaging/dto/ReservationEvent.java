package com.kok.review.infrastructure.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationEvent(
        UUID eventId,
        String eventType,      // "RESERVATION_VISITED" 등
        UUID reservationId,
        UUID userId,
        UUID storeId,
        LocalDateTime visitedAt
) {}
