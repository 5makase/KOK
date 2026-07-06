package com.omakase.kok.aiops.common.advice;

import com.omakase.kok.aiops.common.dto.ApiResponse;
import com.omakase.kok.aiops.common.exception.AiOpsErrorCode;
import com.omakase.kok.aiops.common.exception.BaseException;
import com.omakase.kok.aiops.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleBaseException(BaseException e) {
        ErrorCode errorCode = e.getErrorCode();

        log.warn("[BaseException] code={}, message={}", errorCode.getCode(), e.getMessage());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getStatus().value(), "[" + errorCode.getCode() + "] " + e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("[UnhandledException]", e);

        AiOpsErrorCode errorCode = AiOpsErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getStatus().value(), "[" + errorCode.getCode() + "] " + errorCode.getMessage()));
    }
}
