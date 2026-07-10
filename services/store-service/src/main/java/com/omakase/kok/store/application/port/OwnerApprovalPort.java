package com.omakase.kok.store.application.port;

import java.util.UUID;

/**
 * user-service OWNER 승인 상태 조회 포트
 */
public interface OwnerApprovalPort {

    // 승인 확인 불가(서비스 장애/타임아웃) 시 예외
    boolean isApproved(UUID ownerId);
}
