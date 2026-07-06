package com.omakase.kok.reservation.infrastructure.security;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.common.exception.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 공용 module의 GlobalExceptionHandler는 AuthorizationDeniedException(Spring Security
 * @PreAuthorize 거부 시 발생)을 처리하지 않아 catch-all Exception 핸들러로 떨어져 403 대신 500이 반환된다.
 * module은 여러 서비스가 공유하므로 spring-security 의존성을 추가할 수 없어, 이미 spring-security를
 * 사용 중인 reservation-service에 한정해 여기서 먼저 가로챈다.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionHandler {

    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(RuntimeException e) {
        log.warn("[AccessDeniedException] {}", e.getMessage());

        return ResponseEntity
                .status(CommonErrorCode.ACCESS_DENIED.getStatus())
                .body(ApiResponse.error(
                        CommonErrorCode.ACCESS_DENIED.getStatus().value(),
                        "[" + CommonErrorCode.ACCESS_DENIED.getCode() + "] "
                                + CommonErrorCode.ACCESS_DENIED.getMessage()
                ));
    }
}
