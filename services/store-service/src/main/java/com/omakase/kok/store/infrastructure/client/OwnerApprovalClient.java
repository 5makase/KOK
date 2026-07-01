package com.omakase.kok.store.infrastructure.client;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.store.infrastructure.client.dto.OwnerApprovalResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * user-service 내부 API 호출 클라이언트
 */
@FeignClient(name = "user-service", path = "/api/v1/internal/users")
public interface OwnerApprovalClient {

    @GetMapping("/{userId}/owner-approval")
    ApiResponse<OwnerApprovalResponse> getApprovalStatus(@PathVariable UUID userId);
}
