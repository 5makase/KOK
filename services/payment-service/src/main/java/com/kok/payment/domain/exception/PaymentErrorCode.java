package com.kok.payment.domain.exception;

import com.omakase.kok.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT-001", "결제 정보를 찾을 수 없습니다."),
    INVALID_PAYMENT_STATUS(HttpStatus.CONFLICT, "PAYMENT-002", "현재 상태에서 처리할 수 없는 요청입니다."),
    PAYMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "PAYMENT-003", "해당 예약에 대한 결제가 이미 존재합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
