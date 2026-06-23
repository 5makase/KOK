package com.omakase.kok.store.application.validator;

import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.auth.RoleAuthorizationUtils;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import org.springframework.stereotype.Component;

import java.util.UUID;

// MASTER bypass + 소유권(ownerId) 검증을 단일 지점에서 처리
// Controller에서 OWNER/MASTER 진입 허가는 완료된 상태로 진입하며, MASTER는 모든 매장 접근 가능
// 소유권 검증은 OWNER 전용
@Component
public class StoreOwnerValidator {

    // MASTER면 소유권 검증 없이 통과, OWNER면 p_stores.owner_id == requesterId 확인
    public void validate(Store store, UUID requesterId, String role, StoreErrorCode errorCode) {
        if (RoleAuthorizationUtils.hasAnyRole(role, AuthConstants.MASTER)) {
            return;
        }
        if (!store.isOwnedBy(requesterId)) {
            throw new BaseException(errorCode);
        }
    }
}
