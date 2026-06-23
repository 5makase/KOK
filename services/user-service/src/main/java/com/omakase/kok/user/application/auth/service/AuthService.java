package com.omakase.kok.user.application.auth.service;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.user.domain.user.entity.User;
import com.omakase.kok.user.domain.user.repository.UserRepository;
import com.omakase.kok.user.global.exception.AuthErrorCode;
import com.omakase.kok.user.global.exception.UserErrorCode;
import com.omakase.kok.user.infrastructure.security.JwtProvider;
import com.omakase.kok.user.infrastructure.security.RefreshTokenStore;
import com.omakase.kok.user.presentation.auth.dto.request.TokenRefreshRequest;
import com.omakase.kok.user.presentation.auth.dto.response.TokenResponse;
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
    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;

    /**
     * 로그인
     *
     * 처리 흐름:
     * 1. username으로 사용자 조회 → 없으면 INVALID_LOGIN_INFO (USER-201)
     * 2. 비밀번호 검증 → 불일치 시 INVALID_PASSWORD (USER-005)
     * 3. Access Token + Refresh Token 발급
     * 4. Refresh Token Redis 저장
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 1. 사용자 조회
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BaseException(UserErrorCode.INVALID_LOGIN_INFO));

        // 2. 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BaseException(UserErrorCode.INVALID_PASSWORD);
        }

        String userId = user.getUserId().toString();
        String username = user.getUsername();
        String role = user.getRole().name();

        // 3. 토큰 발급
        String accessToken = jwtProvider.generateAccessToken(userId, username, role);
        String refreshToken = jwtProvider.generateRefreshToken(userId, username, role);

        // 4. Refresh Token Redis 저장
        refreshTokenStore.save(userId, refreshToken);

        return LoginResponse.of(user, accessToken, refreshToken);
    }

    /**
     * Access Token 재발급
     *
     * 처리 흐름:
     * 1. Refresh Token 서명·형식·만료 검증 → INVALID_REFRESH_TOKEN (AUTH-001)
     * 2. Redis 저장 여부 확인 → REFRESH_TOKEN_NOT_FOUND (AUTH-002)
     * 3. Redis 저장 값과 일치 여부 확인 → REFRESH_TOKEN_MISMATCH (AUTH-003) + 강제 로그아웃
     * 4. 새 Access Token 발급
     */
    public TokenResponse.Refresh refresh(TokenRefreshRequest request) {
        String refreshToken = request.refreshToken();

        // 1. 서명·형식·만료 검증
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new BaseException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 2. userId 추출 후 Redis 존재 여부 확인
        String userId = jwtProvider.extractUserId(refreshToken);
        String storedToken = refreshTokenStore.findByUserId(userId)
                .orElseThrow(() -> new BaseException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));

        // 3. 저장된 토큰과 불일치 → 탈취 의심, Redis 토큰 즉시 삭제 (강제 로그아웃)
        if (!storedToken.equals(refreshToken)) {
            refreshTokenStore.deleteByUserId(userId);
            throw new BaseException(AuthErrorCode.REFRESH_TOKEN_MISMATCH);
        }

        // 4. 새 Access Token 발급
        String username = jwtProvider.extractUsername(refreshToken);
        String role = jwtProvider.extractRole(refreshToken);
        String newAccessToken = jwtProvider.generateAccessToken(userId, username, role);

        return TokenResponse.Refresh.of(newAccessToken, refreshToken);
    }
}