package com.kok.review.presentation.DTO1.response;

import com.kok.review.domain.entity.ReviewReply;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ReviewReplyResponseDto {
    private UUID replyId;
    private UUID reviewId;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReviewReplyResponseDto from(ReviewReply reply) {
        return ReviewReplyResponseDto.builder()
                .replyId(reply.getReplyId())
                .reviewId(reply.getReviewId())
                .content(reply.getContent())
                .createdAt(reply.getCreatedAt())
                .updatedAt(reply.getUpdatedAt())
                .build();
    }
}