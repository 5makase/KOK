package com.omakase.kok.reservation.domain.exception;

import com.omakase.kok.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SlotErrorCode implements ErrorCode {

    SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "SLOT-001", "슬롯을 찾을 수 없습니다."),
    SLOT_HAS_ACTIVE_RESERVATION(HttpStatus.CONFLICT, "SLOT-002", "활성 예약이 존재하여 슬롯을 삭제할 수 없습니다."),
    SLOT_CAPACITY_BELOW_USED(HttpStatus.CONFLICT, "SLOT-003", "최대 인원을 현재 예약된 인원보다 작게 줄일 수 없습니다."),
    SLOT_STORE_NOT_OPEN(HttpStatus.UNPROCESSABLE_ENTITY, "SLOT-004", "영업 중인 매장에만 슬롯을 생성할 수 있습니다."),
    SLOT_ON_DAY_OFF(HttpStatus.UNPROCESSABLE_ENTITY, "SLOT-005", "정기 휴무일에 슬롯을 생성할 수 없습니다."),
    SLOT_OUTSIDE_BUSINESS_HOURS(HttpStatus.UNPROCESSABLE_ENTITY, "SLOT-006", "영업시간 외에 슬롯을 생성할 수 없습니다."),
    SLOT_IN_BREAK_TIME(HttpStatus.UNPROCESSABLE_ENTITY, "SLOT-007", "브레이크 타임에 슬롯을 생성할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
