package com.omakase.kok.aiops.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiOpsErrorCode implements ErrorCode {

    INVALID_API_KEY(HttpStatus.UNAUTHORIZED, "AIOPS-001", "유효하지 않은 API 키입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AIOPS-999", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
