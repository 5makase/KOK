package com.kok.review.presentation.DTO1.response;

import com.kok.review.domain.entity.Review;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ReviewDeletedResponseDto {
    private UUID reviewId;
    private LocalDateTime deletedAt;
    public static ReviewDeletedResponseDto from(Review review) {
        return ReviewDeletedResponseDto.builder()
                .reviewId(review.getReviewId())
                .deletedAt(review.getDeletedAt())
                .build();
    }
}
