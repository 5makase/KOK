package com.omakase.kok.user.application.user.service;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.user.domain.user.entity.User;
import com.omakase.kok.user.domain.user.repository.UserRepository;
import com.omakase.kok.user.global.exception.UserErrorCode;
import com.omakase.kok.user.infrastructure.security.JwtTokenProvider;
import com.omakase.kok.user.presentation.user.dto.request.LoginRequest;
import com.omakase.kok.user.presentation.user.dto.response.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BaseException(UserErrorCode.INVALID_LOGIN_INFO));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BaseException(UserErrorCode.INVALID_LOGIN_INFO);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user);
        String refreshToken = jwtTokenProvider.createRefreshToken(user);

        return LoginResponse.of(user, accessToken, refreshToken);
    }
}