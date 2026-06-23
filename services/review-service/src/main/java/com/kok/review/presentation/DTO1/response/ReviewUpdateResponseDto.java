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
public class ReviewUpdateResponseDto {
    private UUID reviewId;
    private BigDecimal rating;
    private LocalDateTime updatedAt;

    public static ReviewUpdateResponseDto from(Review review){
        return ReviewUpdateResponseDto.builder()
                .reviewId(review.getReviewId())
                .rating(review.getRating())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
