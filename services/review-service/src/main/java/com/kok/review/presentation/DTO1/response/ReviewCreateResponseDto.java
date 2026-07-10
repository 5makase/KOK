package com.kok.review.presentation.DTO1.response;

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
public class ReviewCreateResponseDto {
    private UUID storeId;
    private UUID reservationId;
    private BigDecimal rating;
    private LocalDateTime createdAt;

    public static ReviewCreateResponseDto form(Review review) {
        return ReviewCreateResponseDto.builder()
                .storeId(review.getStoreId())
                .reservationId(review.getReservationId())
                .rating(review.getRating())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
