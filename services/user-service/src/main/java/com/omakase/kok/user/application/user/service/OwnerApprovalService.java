package com.omakase.kok.user.application.user.service;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.user.domain.user.entity.OwnerApproval;
import com.omakase.kok.user.domain.user.enums.ApprovalStatus;
import com.omakase.kok.user.domain.user.repository.OwnerApprovalRepository;
import com.omakase.kok.user.domain.user.repository.UserRepository;
import com.omakase.kok.user.global.exception.UserErrorCode;
import com.omakase.kok.user.presentation.user.dto.request.ApprovalRequest;
import com.omakase.kok.user.presentation.user.dto.response.OwnerApprovalResponse;
import com.omakase.kok.user.presentation.user.dto.response.OwnerApprovalStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OwnerApprovalService {

    private final OwnerApprovalRepository ownerApprovalRepository;
    private final UserRepository userRepository;

    // OWNER 승인 상태 조회 (내부 API 전용)
    // 승인 이력이 없는 경우(재신청 이전 등)는 예외가 아니라 approved=false로 응답한다. (안1 확정)
    // 유저 자체가 존재하지 않는 경우에만 예외를 던진다.
    // approved 여부(status == APPROVED) 계산은 OwnerApprovalStatusResponse.from()에 위임한다.
    public OwnerApprovalStatusResponse getApprovalStatus(UUID userId) {
        validateUserExists(userId);

        ApprovalStatus latestStatus = ownerApprovalRepository
                .findFirstByUser_UserIdOrderByCreatedAtDesc(userId)
                .map(OwnerApproval::getStatus)
                .orElse(null);

        return OwnerApprovalStatusResponse.from(userId, latestStatus);
    }

    private void validateUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new BaseException(UserErrorCode.USER_NOT_FOUND);
        }
    }

    @Transactional
    public OwnerApprovalResponse approveOwner(UUID approvalId, UUID masterUserId) {
        OwnerApproval approval = getApprovalOrThrow(approvalId);
        validatePending(approval);
        approval.approve(masterUserId);
        return OwnerApprovalResponse.from(approval);
    }

    @Transactional
    public OwnerApprovalResponse rejectOwner(UUID approvalId, UUID masterUserId, ApprovalRequest request) {
        OwnerApproval approval = getApprovalOrThrow(approvalId);
        validatePending(approval);
        approval.reject(masterUserId, request.rejectReason());
        return OwnerApprovalResponse.from(approval);
    }

    private OwnerApproval getApprovalOrThrow(UUID approvalId) {
        return ownerApprovalRepository.findById(approvalId)
                .orElseThrow(() -> new BaseException(UserErrorCode.OWNER_APPROVAL_NOT_FOUND));
    }

    private void validatePending(OwnerApproval approval) {
        if (approval.getStatus() == ApprovalStatus.APPROVED) {
            throw new BaseException(UserErrorCode.OWNER_ALREADY_APPROVED);
        }
        if (approval.getStatus() == ApprovalStatus.REJECTED) {
            throw new BaseException(UserErrorCode.OWNER_ALREADY_REJECTED);
        }
    }
}