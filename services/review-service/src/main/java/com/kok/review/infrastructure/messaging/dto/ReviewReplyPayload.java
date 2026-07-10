package com.kok.review.infrastructure.messaging.dto;

import java.util.UUID;
//사장님 답글 시 payload
public record ReviewReplyPayload(
        UUID reviewId,
        UUID storeId,
        String storeName,
        UUID userId,
        String replyContent
) implements ReviewEventPayloadType {
    public static ReviewReplyPayload of(UUID reviewId, UUID storeId, String storeName, UUID userId,String replyContent){
        return new ReviewReplyPayload(reviewId, storeId, storeName, userId, replyContent);
    }
}
