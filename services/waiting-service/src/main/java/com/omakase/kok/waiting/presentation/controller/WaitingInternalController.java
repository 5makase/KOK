package com.omakase.kok.waiting.presentation.controller;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.waiting.application.service.WaitingService;
import com.omakase.kok.waiting.application.service.WaitingSettingService;
import com.omakase.kok.waiting.presentation.dto.request.WaitingSettingInitializeRequest;
import com.omakase.kok.waiting.presentation.dto.response.NearTurnWaitingResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingSettingInitializeResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingSettingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/waitings")
@Tag(name = "Waiting Internal", description = "서비스 내부 호출용 웨이팅 API")
public class WaitingInternalController {
    private final WaitingService waitingService;
    private final WaitingSettingService waitingSettingService;

    @Operation(summary = "매장 웨이팅 설정 조회", description = "내부 서비스에서 매장의 웨이팅 설정을 조회")
    @GetMapping("/stores/{storeId}/settings")
    public ResponseEntity<ApiResponse<WaitingSettingResponse>> getWaitingSetting(
            @PathVariable UUID storeId
    ) {
        WaitingSettingResponse response = waitingSettingService.getWaitingSetting(storeId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "순번 임박 알림 대상 조회", description = "현재 순번이 임계값 이내인 웨이팅 목록을 조회")
    @GetMapping("/stores/{storeId}/near-turn")
    public ResponseEntity<ApiResponse<List<NearTurnWaitingResponse>>> getNearTurnWaitings(
            @PathVariable UUID storeId,
            @RequestParam(defaultValue = "3") @Min(1) @Max(50) int threshold
    ) {
        List<NearTurnWaitingResponse> response = waitingService.getNearTurnWaitings(storeId, threshold);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "매장 웨이팅 설정 초기화", description = "내부 서비스에서 매장의 웨이팅 설정을 초기화")
    @PostMapping("/stores/{storeId}/settings")
    public ResponseEntity<ApiResponse<WaitingSettingInitializeResponse>> initializeWaitingSetting(
            @PathVariable UUID storeId,
            @Valid @RequestBody WaitingSettingInitializeRequest request
    ) {
        WaitingSettingInitializeResponse response = waitingSettingService.initializeWaitingSetting(storeId, request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
