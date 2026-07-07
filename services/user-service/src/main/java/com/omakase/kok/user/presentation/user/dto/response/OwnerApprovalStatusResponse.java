package com.omakase.kok.user.presentation.user.dto.response;

import java.util.UUID;

// OWNER 승인 상태 조회 내부 API 응답 (/api/v1/internal/users/{userId}/owner-approval)
public record OwnerApprovalStatusResponse(
        UUID userId,
        boolean approved
) {
    public static OwnerApprovalStatusResponse of(UUID userId, boolean approved) {
        return new OwnerApprovalStatusResponse(userId, approved);
    }
}