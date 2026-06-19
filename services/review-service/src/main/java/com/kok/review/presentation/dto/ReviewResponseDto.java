package com.kok.review.presentation.dto;

import com.kok.review.domain.entity.Review;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ReviewResponseDto {
    private UUID storeId;
    private UUID reservationId;
    private BigDecimal rating;
    private LocalDateTime createdAt;

    public static ReviewResponseDto form(Review review) {
        return ReviewResponseDto.builder()
                .storeId(review.getStoreId())
                .reservationId(review.getReservationId())
                .rating(review.getRating())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
