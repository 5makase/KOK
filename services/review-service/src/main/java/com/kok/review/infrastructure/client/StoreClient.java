package com.kok.review.infrastructure.client;

import com.kok.review.infrastructure.client.dto.StoreResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "store-service")
public interface StoreClient {
    //Store 단일 조회
    @GetMapping("/api/v1/internal/stores/{storeId}")
    StoreResponse getStore(@PathVariable("storeId") UUID storeId);
}
