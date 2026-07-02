package com.omakase.kok.store.presentation;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.store.application.StoreHoursService;
import com.omakase.kok.store.application.StoreService;
import com.omakase.kok.store.application.result.StoreSummaryResult;
import com.omakase.kok.store.presentation.dto.response.BusinessHoursValidationResponse;
import com.omakase.kok.store.presentation.dto.response.StoreSummaryResponse;
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
@RestController
@RequestMapping("/api/v1/internal/stores")
@RequiredArgsConstructor
public class StoreInternalController {

    private final StoreService storeService;
    private final StoreHoursService storeHoursService;

    @GetMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreSummaryResponse>> getStoreSummary(
            @PathVariable UUID storeId
    ) {
        StoreSummaryResult result = storeService.getStoreSummary(storeId);
        return ResponseEntity.ok(ApiResponse.success(StoreSummaryResponse.from(result)));
    }

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
