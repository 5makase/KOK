package com.omakase.kok.reservation.domain.exception;

import com.omakase.kok.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SlotErrorCode implements ErrorCode {

    SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "SLOT-001", "슬롯을 찾을 수 없습니다."),
    SLOT_HAS_ACTIVE_RESERVATION(HttpStatus.CONFLICT, "SLOT-002", "활성 예약이 존재하여 슬롯을 삭제할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
