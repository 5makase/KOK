package com.omakase.kok.store.presentation;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.store.application.StoreService;
import com.omakase.kok.store.application.result.StoreSummaryResult;
import com.omakase.kok.store.presentation.dto.response.StoreSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 서비스 간 내부 호출 전용 - todo Gateway 인증 필터 스킵 필요
@RestController
@RequestMapping("/api/v1/internal/stores")
@RequiredArgsConstructor
public class StoreInternalController {

    private final StoreService storeService;

    @GetMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreSummaryResponse>> getStoreSummary(
            @PathVariable UUID storeId
    ) {
        StoreSummaryResult result = storeService.getStoreSummary(storeId);
        return ResponseEntity.ok(ApiResponse.success(StoreSummaryResponse.from(result)));
    }
}
