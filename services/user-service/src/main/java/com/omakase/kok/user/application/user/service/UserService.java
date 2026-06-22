package com.omakase.kok.user.application.user.service;

import com.omakase.kok.user.domain.user.entity.OwnerApproval;
import com.omakase.kok.user.domain.user.entity.User;
import com.omakase.kok.user.domain.user.enums.ApprovalStatus;
import com.omakase.kok.user.domain.user.enums.Role;
import com.omakase.kok.user.domain.user.repository.OwnerApprovalRepository;
import com.omakase.kok.user.domain.user.repository.UserRepository;
import com.omakase.kok.user.presentation.user.dto.request.SignupRequest;
import com.omakase.kok.user.presentation.user.dto.response.SignupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.user.global.exception.UserErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final OwnerApprovalRepository ownerApprovalRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signupUser(SignupRequest request) {
        if (request.role() == Role.MASTER) {
            throw new BaseException(UserErrorCode.SIGNUP_ROLE_NOT_ALLOWED);
        }
        if (request.role() != Role.USER) {
            throw new BaseException(UserErrorCode.SIGNUP_ROLE_NOT_ALLOWED);
        }

        validateDuplicateUsername(request.username());
        validateDuplicateEmail(request.email());

        User user = createUser(request, Role.USER);
        User savedUser = userRepository.save(user);

        return SignupResponse.from(savedUser, "USER 회원가입이 완료되었습니다.");
    }

    @Transactional
    public SignupResponse signupOwner(SignupRequest request) {
        if (request.role() == Role.MASTER) {
            throw new BaseException(UserErrorCode.SIGNUP_ROLE_NOT_ALLOWED);
        }
        if (request.role() != Role.OWNER) {
            throw new BaseException(UserErrorCode.SIGNUP_ROLE_NOT_ALLOWED);
        }

        validateDuplicateUsername(request.username());
        validateDuplicateEmail(request.email());

        User owner = createUser(request, Role.OWNER);
        User savedOwner = userRepository.save(owner);

        OwnerApproval approval = OwnerApproval.builder()
                .user(savedOwner)
                .status(ApprovalStatus.PENDING)
                .build();

        ownerApprovalRepository.save(approval);

        return SignupResponse.from(savedOwner, "OWNER 회원가입 요청이 완료되었습니다. 승인 대기 상태입니다.");
    }

    private User createUser(SignupRequest request, Role role) {
        return User.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .email(request.email())
                .phone(request.phone())
                .slackId(request.slackId())
                .role(role)
                .build();
    }

    private void validateDuplicateUsername(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new BaseException(UserErrorCode.DUPLICATE_EMAIL);
        }
    }

    private void validateDuplicateEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new BaseException(UserErrorCode.DUPLICATE_EMAIL);
        }
    }
}