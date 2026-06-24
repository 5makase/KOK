package com.omakase.kok.user.application.user.service;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.user.domain.user.entity.User;
import com.omakase.kok.user.domain.user.enums.ApprovalStatus;
import com.omakase.kok.user.domain.user.repository.OwnerApprovalRepository;
import com.omakase.kok.user.domain.user.repository.UserRepository;
import com.omakase.kok.user.global.exception.UserErrorCode;
import com.omakase.kok.user.presentation.user.dto.response.UserDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;
    private final OwnerApprovalRepository ownerApprovalRepository;

    // 내 정보 조회 (본인)
    public UserDetailResponse getMyInfo(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(UserErrorCode.USER_NOT_FOUND));
        return UserDetailResponse.from(user);
    }

    // 특정 회원 조회 (MASTER 전용)
    public UserDetailResponse getUserById(UUID targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BaseException(UserErrorCode.USER_NOT_FOUND));
        return UserDetailResponse.from(user);
    }

}
