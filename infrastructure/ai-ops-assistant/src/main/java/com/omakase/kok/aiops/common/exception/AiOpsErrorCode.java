package com.omakase.kok.aiops.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiOpsErrorCode implements ErrorCode {

    INVALID_API_KEY(HttpStatus.UNAUTHORIZED, "AIOPS-001", "유효하지 않은 API 키입니다."),
    INVALID_SERVICE_NAME(HttpStatus.BAD_REQUEST, "AIOPS-002", "유효하지 않은 서비스명입니다."),
    QUERY_TOO_LONG(HttpStatus.BAD_REQUEST, "AIOPS-003", "PromQL 쿼리가 너무 깁니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AIOPS-999", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
