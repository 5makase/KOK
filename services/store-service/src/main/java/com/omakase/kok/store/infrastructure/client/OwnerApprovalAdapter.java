package com.omakase.kok.store.infrastructure.client;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.application.port.OwnerApprovalPort;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import com.omakase.kok.store.infrastructure.client.dto.OwnerApprovalResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * OwnerApprovalPort 구현체: OwnerApprovalClient(FeignClient)를 호출해 응답을 Port 계약에 맞게 변환
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OwnerApprovalAdapter implements OwnerApprovalPort {

    private final OwnerApprovalClient ownerApprovalClient;

    @CircuitBreaker(name = "ownerApprovalClient", fallbackMethod = "isApprovedFallback")
    @Override
    public boolean isApproved(UUID ownerId) {
        // 인프라 예외(FeignException·타임아웃)는 CB까지 전파 → 실패율 집계됨
        ApiResponse<OwnerApprovalResponse> response = ownerApprovalClient.getApprovalStatus(ownerId);
        OwnerApprovalResponse data = response.getData();

        // 응답 형식 오류만 BaseException
        if (data == null || data.getApproved() == null) {
            throw new BaseException(StoreErrorCode.OWNER_APPROVAL_CHECK_FAILED);
        }
        return data.getApproved();
    }

    // CB OPEN 또는 인프라 장애(FeignException, 타임아웃) 시 호출
    private boolean isApprovedFallback(UUID ownerId, Throwable t) {
        log.error("[OwnerApproval][CB] circuit open or timeout - ownerId: {}, cause: {}", ownerId, t.getMessage());
        throw new BaseException(StoreErrorCode.OWNER_APPROVAL_CHECK_FAILED);
    }
}
