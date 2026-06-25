package com.omakase.kok.user.auth.application.service;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.user.application.auth.service.AuthService;
import com.omakase.kok.user.domain.user.entity.User;
import com.omakase.kok.user.domain.user.enums.Role;
import com.omakase.kok.user.domain.user.repository.UserRepository;
import com.omakase.kok.user.global.exception.AuthErrorCode;
import com.omakase.kok.user.global.exception.UserErrorCode;
import com.omakase.kok.user.infrastructure.security.JwtProvider;
import com.omakase.kok.user.infrastructure.security.RefreshTokenStore;
import com.omakase.kok.user.presentation.auth.dto.request.TokenRefreshRequest;
import com.omakase.kok.user.presentation.auth.dto.response.TokenResponse;
import com.omakase.kok.user.presentation.user.dto.request.LoginRequest;
import com.omakase.kok.user.presentation.user.dto.response.LoginResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;

@DisplayName("AuthService 테스트")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    // ── 로그인 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login()")
    class Login {

        private static final String USERNAME = "testuser";
        private static final String RAW_PASSWORD = "Password1!";
        private static final String ENCODED_PASSWORD = "$2a$encoded";
        private static final String ACCESS_TOKEN = "access.token";
        private static final String REFRESH_TOKEN = "refresh.token";

        private User mockUser() {
            return User.builder()
                    .userId(UUID.randomUUID())
                    .username(USERNAME)
                    .password(ENCODED_PASSWORD)
                    .name("테스트")
                    .email("test@test.com")
                    .phone("010-0000-0000")
                    .role(Role.USER)
                    .build();
        }

        @Test
        @DisplayName("성공 - Access Token + Refresh Token 발급 및 Redis 저장")
        void success() {
            // given
            User user = mockUser();
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(true);
            given(jwtProvider.generateAccessToken(user.getUserId().toString(), USERNAME, "USER")).willReturn(ACCESS_TOKEN);
            given(jwtProvider.generateRefreshToken(user.getUserId().toString(), USERNAME, "USER")).willReturn(REFRESH_TOKEN);

            // when
            LoginResponse response = authService.login(new LoginRequest(USERNAME, RAW_PASSWORD));

            // then
            assertThat(response.userId()).isEqualTo(user.getUserId());
            assertThat(response.username()).isEqualTo(USERNAME);
            assertThat(response.role()).isEqualTo("USER");
            assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);
            then(refreshTokenStore).should().save(user.getUserId().toString(), REFRESH_TOKEN);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 사용자 → UserErrorCode.INVALID_LOGIN_INFO(USER-201)")
        void fail_userNotFound() {
            // given
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.login(new LoginRequest(USERNAME, RAW_PASSWORD)))
                    .isInstanceOf(BaseException.class)
                    .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.INVALID_LOGIN_INFO));
        }

        @Test
        @DisplayName("실패 - 비밀번호 불일치 → UserErrorCode.INVALID_LOGIN_INFO(USER-201)")
        void fail_wrongPassword() {
            // given
            User user = mockUser();
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.login(new LoginRequest(USERNAME, RAW_PASSWORD)))
                    .isInstanceOf(BaseException.class)
                    .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.INVALID_LOGIN_INFO));
        }
    }

    // ── 재발급 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refresh()")
    class Refresh {

        private static final String REFRESH_TOKEN = "valid.refresh.token";
        private static final String STORED_DIGEST = "hashed.digest.value"; // Redis에 저장된 digest 값
        private static final String USER_ID = "user-uuid-001";
        private static final String USERNAME = "testuser";
        private static final String ROLE = "USER";
        private static final String NEW_ACCESS_TOKEN = "new.access.token";

        @Test
        @DisplayName("성공 - 유효한 Refresh Token으로 새 Access Token 발급")
        void success() {
            // given
            given(jwtProvider.validateToken(REFRESH_TOKEN)).willReturn(true);
            given(jwtProvider.isRefreshToken(REFRESH_TOKEN)).willReturn(true);
            given(jwtProvider.extractUserId(REFRESH_TOKEN)).willReturn(USER_ID);
            given(refreshTokenStore.findByUserId(USER_ID)).willReturn(Optional.of(STORED_DIGEST)); // digest 반환
            given(refreshTokenStore.digest(REFRESH_TOKEN)).willReturn(STORED_DIGEST); // digest 비교
            given(jwtProvider.extractUsername(REFRESH_TOKEN)).willReturn(USERNAME);
            given(jwtProvider.extractRole(REFRESH_TOKEN)).willReturn(ROLE);
            given(jwtProvider.generateAccessToken(USER_ID, USERNAME, ROLE)).willReturn(NEW_ACCESS_TOKEN);

            // when
            TokenResponse.Refresh response = authService.refresh(new TokenRefreshRequest(REFRESH_TOKEN));

            // then
            assertThat(response.accessToken()).isEqualTo(NEW_ACCESS_TOKEN);
            assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);
        }

        @Test
        @DisplayName("실패 - 위조되거나 만료된 Refresh Token → AuthErrorCode.INVALID_REFRESH_TOKEN(AUTH-001)")
        void fail_invalidRefreshToken() {
            // given
            given(jwtProvider.validateToken(REFRESH_TOKEN)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.refresh(new TokenRefreshRequest(REFRESH_TOKEN)))
                    .isInstanceOf(BaseException.class)
                    .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode())
                            .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
        }

        @Test
        @DisplayName("실패 - Access Token을 Refresh Token 자리에 제출 → AuthErrorCode.INVALID_REFRESH_TOKEN(AUTH-001)") // ✅ 추가
        void fail_accessTokenSubmittedAsRefreshToken() {
            // given
            given(jwtProvider.validateToken(REFRESH_TOKEN)).willReturn(true);
            given(jwtProvider.isRefreshToken(REFRESH_TOKEN)).willReturn(false); // access 토큰으로 판별

            // when & then
            assertThatThrownBy(() -> authService.refresh(new TokenRefreshRequest(REFRESH_TOKEN)))
                    .isInstanceOf(BaseException.class)
                    .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode())
                            .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));

            // Redis 조회·삭제가 일어나지 않아야 함
            then(refreshTokenStore).should(never()).findByUserId(USER_ID);
            then(refreshTokenStore).should(never()).deleteByUserId(USER_ID);
        }

        @Test
        @DisplayName("실패 - Redis에 Refresh Token 없음 → AuthErrorCode.REFRESH_TOKEN_NOT_FOUND(AUTH-002)")
        void fail_refreshTokenNotFound() {
            // given
            given(jwtProvider.validateToken(REFRESH_TOKEN)).willReturn(true);
            given(jwtProvider.isRefreshToken(REFRESH_TOKEN)).willReturn(true);
            given(jwtProvider.extractUserId(REFRESH_TOKEN)).willReturn(USER_ID);
            given(refreshTokenStore.findByUserId(USER_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.refresh(new TokenRefreshRequest(REFRESH_TOKEN)))
                    .isInstanceOf(BaseException.class)
                    .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode())
                            .isEqualTo(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));
        }

        @Test
        @DisplayName("실패 - Redis 저장 토큰과 불일치 → AuthErrorCode.REFRESH_TOKEN_MISMATCH(AUTH-003) + Redis 삭제")
        void fail_refreshTokenMismatch_andDeleteStoredToken() {
            // given
            String differentDigest = "different.digest.value";
            given(jwtProvider.validateToken(REFRESH_TOKEN)).willReturn(true);
            given(jwtProvider.isRefreshToken(REFRESH_TOKEN)).willReturn(true);
            given(jwtProvider.extractUserId(REFRESH_TOKEN)).willReturn(USER_ID);
            given(refreshTokenStore.findByUserId(USER_ID)).willReturn(Optional.of(differentDigest)); // digest 반환
            given(refreshTokenStore.digest(REFRESH_TOKEN)).willReturn(STORED_DIGEST); // 다른 digest → 불일치

            // when & then
            assertThatThrownBy(() -> authService.refresh(new TokenRefreshRequest(REFRESH_TOKEN)))
                    .isInstanceOf(BaseException.class)
                    .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode())
                            .isEqualTo(AuthErrorCode.REFRESH_TOKEN_MISMATCH));

            then(refreshTokenStore).should().deleteByUserId(USER_ID);
            then(jwtProvider).should(never()).generateAccessToken(any(), any(), any()); // 인자 무관하게 호출 안됨 검증
        }
    }
}