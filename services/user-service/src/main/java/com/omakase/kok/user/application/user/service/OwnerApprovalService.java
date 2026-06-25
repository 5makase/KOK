package com.omakase.kok.user.application.user.service;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.user.domain.user.entity.OwnerApproval;
import com.omakase.kok.user.domain.user.enums.ApprovalStatus;
import com.omakase.kok.user.domain.user.repository.OwnerApprovalRepository;
import com.omakase.kok.user.global.exception.UserErrorCode;
import com.omakase.kok.user.presentation.user.dto.request.ApprovalRequest;
import com.omakase.kok.user.presentation.user.dto.response.OwnerApprovalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OwnerApprovalService {

    private final OwnerApprovalRepository ownerApprovalRepository;

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