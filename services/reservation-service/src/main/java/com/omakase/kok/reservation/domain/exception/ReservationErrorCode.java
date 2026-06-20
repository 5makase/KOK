package com.omakase.kok.reservation.domain.exception;

import com.omakase.kok.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReservationErrorCode implements ErrorCode {

    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "RESERVATION-001", "예약을 찾을 수 없습니다."),
    SLOT_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "RESERVATION-002", "예약 가능 인원이 부족합니다."),
    SLOT_LOCK_FAILED(HttpStatus.CONFLICT, "RESERVATION-003", "슬롯 예약 처리 중입니다. 잠시 후 다시 시도해주세요."),
    SLOT_UNAVAILABLE(HttpStatus.CONFLICT, "RESERVATION-004", "예약 불가능한 슬롯입니다."),
    PAYMENT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "RESERVATION-005", "결제 처리 중 오류가 발생했습니다."),
    PAYMENT_METHOD_REQUIRED(HttpStatus.BAD_REQUEST, "RESERVATION-006", "예약금이 필요한 슬롯은 결제 수단을 입력해야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
