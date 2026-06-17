package com.omakase.kok.store.global.exception;

import com.omakase.kok.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StoreErrorCode implements ErrorCode {

    // 매장
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE-001", "매장을 찾을 수 없습니다."),
    STORE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "STORE-002", "본인 매장만 수정할 수 있습니다."),
    INVALID_STORE_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "STORE-003", "허용되지 않는 매장 상태 전이입니다."),
    STORE_NOT_OPEN(HttpStatus.BAD_REQUEST, "STORE-004", "영업 중인 매장이 아닙니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE-005", "카테고리를 찾을 수 없습니다."),
    INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "STORE-006", "소분류 카테고리만 선택할 수 있습니다."),

    // 메뉴
    MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE-101", "메뉴를 찾을 수 없습니다."),
    MENU_ACCESS_DENIED(HttpStatus.FORBIDDEN, "STORE-102", "본인 매장의 메뉴만 수정할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
