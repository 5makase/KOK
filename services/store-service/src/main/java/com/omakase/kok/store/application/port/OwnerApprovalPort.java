package com.omakase.kok.store.application.port;

import java.util.UUID;

/**
 * user-service OWNER 승인 상태 조회 포트 -> 구현체: store/infrastructure/client/OwnerApprovalAdapter
 */
public interface OwnerApprovalPort {

    // OWNER 승인 완료 여부 반환: 승인 요청 없음(404) / 통신 실패 시 → OWNER_APPROVAL_CHECK_FAILED 예외
    boolean isApproved(UUID ownerId);
}
