package com.omakase.kok.user.global.exception;

import com.omakase.kok.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 인증/인가 관련 에러 코드
 * - module의 ErrorCode 인터페이스를 상속 (공통 모듈 정책)
 * - UserErrorCode와 동일한 방식으로 정의
 *
 * 정책 6.2:
 * - 만료/위조 토큰    → 401
 * - 권한 부족         → 403
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    // Refresh Token
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-001", "유효하지 않은 Refresh Token입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH-002", "Refresh Token이 존재하지 않습니다. 다시 로그인해주세요."),
    REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "AUTH-003", "Refresh Token이 일치하지 않습니다. 다시 로그인해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}