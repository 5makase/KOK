package com.omakase.kok.waiting.global.exception;

import com.omakase.kok.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WaitingErrorCode implements ErrorCode {
    WAITING_NOT_FOUND(HttpStatus.NOT_FOUND, "WAITING-001", "웨이팅을 찾을 수 없습니다."),
    WAITING_ALREADY_EXISTS(HttpStatus.CONFLICT, "WAITING-002", "이미 해당 매장에 대기 중인 웨이팅이 있습니다."),
    WAITING_DISABLED(HttpStatus.BAD_REQUEST, "WAITING-003", "웨이팅을 사용할 수 없는 매장입니다."),
    WAITING_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "WAITING-004", "최대 웨이팅 가능 팀 수를 초과했습니다."),
    WAITING_OWN_STORE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "WAITING-005", "본인 매장에는 웨이팅을 등록할 수 없습니다."),
    WAITING_CANCEL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "WAITING-006", "취소할 수 없는 웨이팅 상태입니다."),
    WAITING_CALL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "WAITING-007", "호출할 수 없는 웨이팅 상태입니다."),
    WAITING_ENTER_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "WAITING-008", "입장 완료 처리할 수 없는 웨이팅 상태입니다."),
    WAITING_NO_SHOW_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "WAITING-009", "미입장 처리할 수 없는 웨이팅 상태입니다."),
    WAITING_CALL_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "WAITING-010", "호출할 다음 웨이팅이 없습니다."),
    WAITING_SETTING_NOT_FOUND(HttpStatus.NOT_FOUND, "WAITING-011", "웨이팅 설정을 찾을 수 없습니다."),
    WAITING_SUMMARY_NOT_FOUND(HttpStatus.NOT_FOUND, "WAITING-012", "웨이팅 요약 정보를 찾을 수 없습니다."),
    WAITING_REGISTER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "WAITING-013", "웨이팅 등록에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
