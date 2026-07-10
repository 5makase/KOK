package com.omakase.kok.reservation.presentation.controller;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.reservation.application.dto.SlotCapacityRestoreResponse;
import com.omakase.kok.reservation.application.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/reservations")
@Tag(name = "Reservation Internal", description = "서비스 내부/관리자 호출용 예약 API")
public class ReservationInternalController {

    private final ReservationService reservationService;

    @Operation(summary = "슬롯 정원 Redis 값 복구",
            description = "Redis 장애로 유실된 slot:capacity:{slotId}를 DB 기준으로 재계산하여 복구한다. " +
                    "slotDate 미지정 시 오늘 이후 OPEN 슬롯 전체가 대상.")
    @PostMapping("/stores/{storeId}/capacity/restore")
    public ResponseEntity<ApiResponse<SlotCapacityRestoreResponse>> restoreSlotCapacity(
            @PathVariable UUID storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate slotDate
    ) {
        SlotCapacityRestoreResponse response = reservationService.restoreCapacity(storeId, slotDate);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
