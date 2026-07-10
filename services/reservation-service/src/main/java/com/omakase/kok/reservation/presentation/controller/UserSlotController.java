package com.omakase.kok.reservation.presentation.controller;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.reservation.application.dto.SlotResponse;
import com.omakase.kok.reservation.application.service.SlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "슬롯 조회 (유저)")
@RestController
@RequestMapping("/api/v1/reservations/slots/{storeId}")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
public class UserSlotController {

    private final SlotService slotService;

    @Operation(summary = "가게별 예약 가능 슬롯 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SlotResponse>>> getAvailableSlots(
            @PathVariable UUID storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(slotService.getAvailableSlots(storeId, date)));
    }
}
