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
            throw new IllegalArgumentException("MASTER 권한은 회원가입을 통해 생성할 수 없습니다.");
        }
        if (request.role() != Role.USER) {
            throw new IllegalArgumentException("USER 회원가입 경로에서는 USER 권한만 가입할 수 있습니다.");
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
            throw new IllegalArgumentException("MASTER 권한은 회원가입을 통해 생성할 수 없습니다.");
        }
        if (request.role() != Role.OWNER) {
            throw new IllegalArgumentException("OWNER 회원가입 경로에서는 OWNER 권한만 가입할 수 있습니다.");
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
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
    }

    private void validateDuplicateEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
    }
}