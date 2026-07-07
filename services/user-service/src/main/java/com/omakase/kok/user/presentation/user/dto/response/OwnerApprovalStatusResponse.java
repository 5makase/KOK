package com.omakase.kok.user.presentation.user.dto.response;

import com.omakase.kok.user.domain.user.enums.ApprovalStatus;

import java.util.UUID;

// OWNER 승인 상태 조회 내부 API 응답 (/api/v1/internal/users/{userId}/owner-approval)
public record OwnerApprovalStatusResponse(
        UUID userId,
        boolean approved
) {

    // 최신 승인 이력의 status(nullable)를 받아 approved 여부(status == APPROVED)를 이 DTO가 직접 계산한다.
    // latestStatus가 null(승인 이력 없음)이어도 "null == APPROVED"는 NPE 없이 false로 평가된다. (안1)
    // Optional을 파라미터로 받지 않기 위해, Optional 해제는 호출부(Service)에서 하고 여기서는 값만 받는다.
    public static OwnerApprovalStatusResponse from(UUID userId, ApprovalStatus latestStatus) {
        boolean approved = latestStatus == ApprovalStatus.APPROVED;
        return new OwnerApprovalStatusResponse(userId, approved);
    }

    // 이미 계산된 approved 값으로 직접 생성할 때 사용 (테스트, 캐시된 값 조립 등)
    public static OwnerApprovalStatusResponse of(UUID userId, boolean approved) {
        return new OwnerApprovalStatusResponse(userId, approved);
    }
}