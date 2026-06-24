package com.omakase.kok.store.application.validator;

import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.auth.RoleAuthorizationUtils;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

// MASTER bypass + 소유권(ownerId) 검증을 단일 지점에서 처리
// Controller의 requireAnyRole과 무관하게 이 Validator 자체가 허용 역할을 보장
// MASTER: 소유권 검증 없이 전체 접근 허용
// OWNER: p_stores.owner_id == requesterId 검증
// 그 외: 역할 자체를 거부 (Controller 우회 경로 방어)
@Slf4j
@Component
public class StoreOwnerValidator {

    public void validate(Store store, UUID requesterId, String role, StoreErrorCode errorCode) {
        if (RoleAuthorizationUtils.hasAnyRole(role, AuthConstants.MASTER)) {
            return;
        }
        if (!RoleAuthorizationUtils.hasAnyRole(role, AuthConstants.OWNER)) {
            // 게이트웨이/컨트롤러 우회로 허용되지 않은 역할이 서비스에 직접 접근한 경우
            log.warn("허용되지 않은 역할로 서비스 직접 접근 시도: role={}, storeId={}, requesterId={}",
                    role, store.getStoreId(), requesterId);
            throw new BaseException(errorCode);
        }
        if (!store.isOwnedBy(requesterId)) {
            throw new BaseException(errorCode);
        }
    }
}
