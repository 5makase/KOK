package com.omakase.kok.waiting.presentation.controller;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.waiting.application.service.StoreWaitingService;
import com.omakase.kok.waiting.presentation.dto.request.WaitingSettingInitializeRequest;
import com.omakase.kok.waiting.presentation.dto.response.WaitingSettingInitializeResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/waitings")
public class WaitingInternalController {
    private final StoreWaitingService storeWaitingService;

    // 매장 웨이팅 설정 초기화
    @PostMapping("/stores/{storeId}/settings")
    public ResponseEntity<ApiResponse<WaitingSettingInitializeResponse>> initializeWaitingSetting(
            @PathVariable UUID storeId,
            @Valid @RequestBody WaitingSettingInitializeRequest request
    ) {
        WaitingSettingInitializeResponse response = storeWaitingService.initializeWaitingSetting(storeId, request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
