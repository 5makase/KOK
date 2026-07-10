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
    PAYMENT_METHOD_REQUIRED(HttpStatus.BAD_REQUEST, "RESERVATION-006", "예약금이 필요한 슬롯은 결제 수단을 입력해야 합니다."),
    RESERVATION_FORBIDDEN(HttpStatus.FORBIDDEN, "RESERVATION-007", "해당 예약에 접근 권한이 없습니다."),
    RESERVATION_NOT_CANCELLABLE(HttpStatus.CONFLICT, "RESERVATION-008", "현재 상태에서는 예약을 취소할 수 없습니다."),
    RESERVATION_NOT_VISITABLE(HttpStatus.CONFLICT, "RESERVATION-009", "CONFIRMED 상태의 예약만 방문/노쇼 처리할 수 있습니다."),
    PAYLOAD_SERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "RESERVATION-010", "이벤트 payload 직렬화에 실패했습니다."),
    RESERVATION_NOT_CONFIRMABLE(HttpStatus.CONFLICT, "RESERVATION-011", "현재 상태에서는 예약을 확정할 수 없습니다."),
    RESERVATION_NOT_CHANGEABLE(HttpStatus.CONFLICT, "RESERVATION-012", "CONFIRMED 상태의 예약만 변경할 수 있습니다."),
    RESERVATION_SLOT_NOT_CHANGEABLE(HttpStatus.CONFLICT, "RESERVATION-013", "새로운 슬롯의 날짜는 오늘 이후여야 합니다."),
    RESERVATION_SLOT_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "RESERVATION-014", "변경하려는 슬롯의 예약 가능 인원이 부족합니다."),
    RESERVATION_SLOT_STORE_MISMATCH(HttpStatus.CONFLICT, "RESERVATION-015", "다른 매장의 슬롯으로는 변경할 수 없습니다."),
    IDEMPOTENCY_KEY_IN_PROGRESS(HttpStatus.CONFLICT, "RESERVATION-016", "이전 요청이 아직 처리 중입니다. 잠시 후 다시 시도해주세요."),
    STATISTICS_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "RESERVATION-017", "통계 조회 기간이 올바르지 않습니다. (from <= to, 최대 3개월)");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
