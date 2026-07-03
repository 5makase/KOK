package com.kok.review.infrastructure.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

// REVIEW_REPORT_RESULT 이벤트 payload (명세서 §2.2)
public record ReviewReportResultPayload(
        UUID reviewId,
        UUID userId,          // 알림 수신자 = 신고자
        String reportResult,  // "APPROVED" / "REJECTED"
        LocalDateTime processedAt
) implements ReviewEventPayloadType {

    public static ReviewReportResultPayload of(UUID reviewId, UUID userId,
                                               String reportResult, LocalDateTime processedAt) {
        return new ReviewReportResultPayload(reviewId, userId, reportResult, processedAt);
    }
}
