package com.omakase.kok.reservation.presentation.controller;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.reservation.application.dto.ReservationStatisticsResponse;
import com.omakase.kok.reservation.application.service.ReservationStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "예약 통계 (점주)")
@RestController
@RequestMapping("/api/v1/stores/{storeId}/statistics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class StoreStatisticsController {

    private final ReservationStatisticsService reservationStatisticsService;

    @Operation(summary = "매장 예약 통계 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<ReservationStatisticsResponse>> getStatistics(
            @PathVariable UUID storeId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(reservationStatisticsService.getStatistics(storeId, from, to)));
    }
}
