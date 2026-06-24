package com.omakase.kok.user.presentation.user.dto.response;

import com.omakase.kok.user.domain.user.entity.OwnerApproval;
import com.omakase.kok.user.domain.user.enums.ApprovalStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record OwnerApprovalResponse(
        UUID approvalId,
        UUID userId,
        String username,
        ApprovalStatus status,
        String rejectReason,
        LocalDateTime processedAt,
        UUID processedBy
) {
    public static OwnerApprovalResponse from(OwnerApproval approval) {
        return new OwnerApprovalResponse(
                approval.getApprovalId(),
                approval.getUser().getUserId(),
                approval.getUser().getUsername(),
                approval.getStatus(),
                approval.getRejectReason(),
                approval.getProcessedAt(),
                approval.getProcessedBy()
        );
    }
}