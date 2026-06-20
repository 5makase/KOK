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
    CATEGORY_HAS_CHILDREN(HttpStatus.CONFLICT, "STORE-007", "하위 카테고리가 존재하여 삭제할 수 없습니다."),
    CATEGORY_HAS_STORES(HttpStatus.CONFLICT, "STORE-008", "해당 카테고리를 사용 중인 매장이 존재하여 삭제할 수 없습니다."),
    CATEGORY_DUPLICATE_NAME(HttpStatus.CONFLICT, "STORE-009", "같은 위치에 동일한 이름의 카테고리가 이미 존재합니다."),
    CATEGORY_DUPLICATE_SORT_ORDER(HttpStatus.CONFLICT, "STORE-010", "같은 위치에 동일한 정렬 순서의 카테고리가 이미 존재합니다."),

    // 메뉴
    MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE-101", "메뉴를 찾을 수 없습니다."),
    MENU_ACCESS_DENIED(HttpStatus.FORBIDDEN, "STORE-102", "본인 매장의 메뉴만 수정할 수 있습니다."),

    // 영업시간
    STORE_HOURS_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE-201", "영업시간을 찾을 수 없습니다."),
    STORE_HOURS_REQUIRED_FOR_OPEN(HttpStatus.BAD_REQUEST, "STORE-202", "영업 중 전환을 위해 영업시간 등록이 필요합니다."),
    INVALID_STORE_HOURS(HttpStatus.BAD_REQUEST, "STORE-203", "영업일에는 영업시간이 필요합니다."),
    STORE_HOURS_ACCESS_DENIED(HttpStatus.FORBIDDEN, "STORE-204", "본인 매장의 영업시간만 수정할 수 있습니다."),
    STORE_HOURS_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "STORE-205", "이미 삭제된 영업시간입니다."),
    STORE_HOURS_CANNOT_DELETE_WHILE_OPEN(HttpStatus.BAD_REQUEST, "STORE-206", "영업 중인 매장의 영업시간은 삭제할 수 없습니다. 휴무일 변경을 이용해 주세요."),

    // 편의시설
    AMENITY_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE-301", "편의시설을 찾을 수 없습니다."),
    AMENITY_ACCESS_DENIED(HttpStatus.FORBIDDEN, "STORE-302", "본인 매장의 편의시설만 수정할 수 있습니다."),
    AMENITY_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "STORE-303", "이미 삭제된 편의시설입니다."),
    AMENITY_ALREADY_EXISTS(HttpStatus.CONFLICT, "STORE-304", "이미 등록된 편의시설입니다."),

    // 이미지
    STORE_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE-401", "이미지를 찾을 수 없습니다."),
    STORE_IMAGE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "STORE-402", "본인 매장의 이미지만 수정할 수 있습니다."),
    STORE_IMAGE_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "STORE-403", "이미 삭제된 이미지입니다."),
    STORE_IMAGE_DUPLICATE_DISPLAY_ORDER(HttpStatus.BAD_REQUEST, "STORE-404", "노출 순서가 중복되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
