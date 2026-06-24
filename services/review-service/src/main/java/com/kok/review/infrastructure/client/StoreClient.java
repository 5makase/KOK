package com.kok.review.infrastructure.client;

import com.kok.review.infrastructure.client.dto.StoreResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "store-service")
public interface StoreClient {
    @GetMapping("/api/v1/stores/{storeId}")
    StoreResponse getStore(@PathVariable("storeId") UUID storeId);
}
