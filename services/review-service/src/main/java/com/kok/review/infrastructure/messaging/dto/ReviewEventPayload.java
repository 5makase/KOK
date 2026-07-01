package com.kok.review.infrastructure.messaging.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.UUID;

// 이벤트 payLoad
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewEventPayload(
        UUID reviewId,
        UUID storeId,
        BigDecimal rating,          // CREATED, UPDATE만포함, DELETED는 null
        BigDecimal averageRating,
        int reviewCount
) implements ReviewEventPayloadType {
    /** REVIEW_CREATED 용 (rating 포함) */
    public static ReviewEventPayload created(UUID reviewId, UUID storeId,
                                             BigDecimal rating,
                                             BigDecimal averageRating, int reviewCount) {
        return new ReviewEventPayload(reviewId, storeId, rating, averageRating, reviewCount);
    }

    /** REVIEW_DELETED 용 (rating 없음) */
    public static ReviewEventPayload deleted(UUID reviewId, UUID storeId,
                                             BigDecimal averageRating, int reviewCount) {
        return new ReviewEventPayload(reviewId, storeId, null, averageRating, reviewCount);
    }
}