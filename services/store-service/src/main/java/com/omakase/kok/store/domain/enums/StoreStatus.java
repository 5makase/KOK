package com.omakase.kok.store.domain.enums;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.global.exception.StoreErrorCode;

public enum StoreStatus {
    PREPARING,            // 등록 후 오픈 전 준비 상태 (기본값)
    OPEN,                 // 정상 영업 중
    CLOSED,               // 휴무
    PERMANENTLY_CLOSED;   // 폐업 (종료 상태, 전이 불가)

    public void validateTransitionTo(StoreStatus next) {
        boolean allowed = switch (this) {
            case PREPARING -> next == OPEN;
            case OPEN -> next == CLOSED || next == PERMANENTLY_CLOSED;
            case CLOSED -> next == OPEN;
            case PERMANENTLY_CLOSED -> false;
        };

        if (!allowed) {
            throw new BaseException(StoreErrorCode.INVALID_STORE_STATUS_TRANSITION);
        }
    }
}
