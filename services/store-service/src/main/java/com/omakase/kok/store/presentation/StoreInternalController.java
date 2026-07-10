package com.omakase.kok.store.presentation;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.store.application.StoreHoursService;
import com.omakase.kok.store.application.StoreService;
import com.omakase.kok.store.application.result.StoreSummaryResult;
import com.omakase.kok.store.presentation.dto.response.BusinessHoursValidationResponse;
import com.omakase.kok.store.presentation.dto.response.StoreSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

// 서비스 간 내부 호출 전용
@Tag(name = "Internal", description = "내부 서비스 통신 API")
@RestController
@RequestMapping("/api/v1/internal/stores")
@RequiredArgsConstructor
public class StoreInternalController {

    private final StoreService storeService;
    private final StoreHoursService storeHoursService;

    @Operation(summary = "매장 요약 정보 조회 (내부용)")
    @GetMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreSummaryResponse>> getStoreSummary(
            @PathVariable UUID storeId
    ) {
        StoreSummaryResult result = storeService.getStoreSummary(storeId);
        return ResponseEntity.ok(ApiResponse.success(StoreSummaryResponse.from(result)));
    }

    @Operation(summary = "영업시간 검증 (내부용)")
    @GetMapping("/{storeId}/hours/validate")
    public ResponseEntity<ApiResponse<BusinessHoursValidationResponse>> validateBusinessHours(
            @PathVariable UUID storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                BusinessHoursValidationResponse.from(storeHoursService.checkBusinessHours(storeId, date, time))));
    }
}
