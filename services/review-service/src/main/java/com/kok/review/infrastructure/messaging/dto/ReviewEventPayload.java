package com.kok.review.infrastructure.messaging.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 이벤트 payload.
 * rating은 CREATED에만 있고 DELETED엔 없으므로 null이면 직렬화에서 제외.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewEventPayload(
        UUID reviewId,
        UUID storeId,
        BigDecimal rating,          // CREATED만 포함, DELETED는 null
        BigDecimal averageRating,
        int reviewCount
) {
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