package com.omakase.kok.common.advice;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.common.exception.CommonErrorCode;
import com.omakase.kok.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleBaseException(BaseException e) {
        ErrorCode errorCode = e.getErrorCode();

        log.warn("[BaseException] code={}, message={}", errorCode.getCode(), e.getMessage());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(
                        errorCode.getStatus().value(),
                        "[" + errorCode.getCode() + "] " + e.getMessage()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("[ValidationException] {}", e.getMessage());

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(
                        CommonErrorCode.INVALID_INPUT_VALUE.getStatus().value(),
                        "[" + CommonErrorCode.INVALID_INPUT_VALUE.getCode() + "] "
                                + CommonErrorCode.INVALID_INPUT_VALUE.getMessage()
                ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("[TypeMismatchException] {}", e.getMessage());

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(
                        CommonErrorCode.INVALID_INPUT_VALUE.getStatus().value(),
                        "[" + CommonErrorCode.INVALID_INPUT_VALUE.getCode() + "] "
                                + CommonErrorCode.INVALID_INPUT_VALUE.getMessage()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("[UnhandledException]", e);

        return ResponseEntity
                .status(CommonErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.error(
                        CommonErrorCode.INTERNAL_SERVER_ERROR.getStatus().value(),
                        "[" + CommonErrorCode.INTERNAL_SERVER_ERROR.getCode() + "] "
                                + CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()
                ));
    }
}