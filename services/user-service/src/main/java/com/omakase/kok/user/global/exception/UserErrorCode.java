package com.omakase.kok.user.global.exception;

import com.omakase.kok.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    // 회원
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-001", "사용자를 찾을 수 없습니다."),
    USER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "USER-002", "해당 사용자에 대한 접근 권한이 없습니다."),
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "USER-003", "이미 사용 중인 사용자 이름입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER-004", "이미 사용 중인 이메일입니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "USER-005", "비밀번호가 올바르지 않습니다."),
    INVALID_ROLE(HttpStatus.BAD_REQUEST, "USER-006", "잘못된 권한입니다."),

    // 회원가입
    SIGNUP_ROLE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "USER-101", "회원가입할 수 없는 권한입니다."),
    OWNER_APPROVAL_REQUIRED(HttpStatus.BAD_REQUEST, "USER-102", "OWNER 계정은 승인 대기 상태로 가입됩니다."),

    // 로그인 / 인증
    INVALID_LOGIN_INFO(HttpStatus.UNAUTHORIZED, "USER-201", "아이디 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "USER-202", "인증이 필요합니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "USER-203", "토큰이 만료되었습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "USER-204", "유효하지 않은 토큰입니다."),

    // OWNER 승인
    OWNER_APPROVAL_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-301", "OWNER 승인 요청을 찾을 수 없습니다."),
    OWNER_ALREADY_APPROVED(HttpStatus.CONFLICT, "USER-302", "이미 승인된 OWNER 요청입니다."),
    OWNER_ALREADY_REJECTED(HttpStatus.CONFLICT, "USER-303", "이미 거절된 OWNER 요청입니다."),
    INVALID_APPROVAL_STATUS(HttpStatus.BAD_REQUEST, "USER-304", "잘못된 승인 상태입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
