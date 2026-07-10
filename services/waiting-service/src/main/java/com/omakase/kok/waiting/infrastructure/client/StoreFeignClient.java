package com.omakase.kok.waiting.infrastructure.client;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.waiting.infrastructure.client.dto.StoreSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "store-service", path = "/api/v1/internal/stores")
public interface StoreFeignClient {
    @GetMapping("/{storeId}")
    ApiResponse<StoreSummaryResponse> getStoreSummary(@PathVariable("storeId") UUID storeId);
}
