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
        try {
            ApiResponse<OwnerApprovalResponse> response = ownerApprovalClient.getApprovalStatus(ownerId);
            OwnerApprovalResponse data = response == null ? null : response.getData();

            // 응답 자체가 없거나 approved 필드가 null이면 조회 실패로 처리
            if (data == null || data.getApproved() == null) {
                throw new BaseException(StoreErrorCode.OWNER_APPROVAL_CHECK_FAILED);
            }
            return data.getApproved();
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("OWNER 승인 상태 조회 실패 - ownerId: {}", ownerId, e);
            throw new BaseException(StoreErrorCode.OWNER_APPROVAL_CHECK_FAILED);
        }
    }

    // circuit OPEN·타임아웃 등 인프라 장애 시 호출
    // ignoreExceptions는 실패율 집계만 제외할 뿐 fallback까지 막지 않으므로 BaseException은 방어적으로 재전파
    private boolean isApprovedFallback(UUID ownerId, Throwable t) {
        if (t instanceof BaseException be) throw be;
        log.error("[OwnerApproval][CB] circuit open or timeout - ownerId: {}, cause: {}", ownerId, t.getMessage());
        throw new BaseException(StoreErrorCode.OWNER_APPROVAL_CHECK_FAILED);
    }
}
