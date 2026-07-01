package com.omakase.kok.reservation.presentation.controller;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.common.dto.PageResponse;
import com.omakase.kok.reservation.application.dto.CancelReservationRequest;
import com.omakase.kok.reservation.application.dto.ReservationResponse;
import com.omakase.kok.reservation.application.service.ReservationService;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "예약 관리 (점주)")
@RestController
@RequestMapping("/api/v1/stores/{storeId}/reservations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class StoreReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "매장 예약 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReservationResponse>>> getStoreReservations(
            @PathVariable UUID storeId,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 50));
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<ReservationResponse> result = reservationService.getStoreReservations(storeId, status, date, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(result)));
    }

    @Operation(summary = "매장 예약 단건 조회")
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationResponse>> getStoreReservation(
            @PathVariable UUID storeId,
            @PathVariable UUID reservationId) {
        return ResponseEntity.ok(ApiResponse.success(reservationService.getStoreReservation(storeId, reservationId)));
    }

    @Operation(summary = "점주 예약 취소")
    @PatchMapping("/{reservationId}/cancel")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancelByStore(
            @PathVariable UUID storeId,
            @PathVariable UUID reservationId,
            @RequestBody(required = false) CancelReservationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(reservationService.cancelByStore(storeId, reservationId, request)));
    }

    @Operation(summary = "방문 처리")
    @PatchMapping("/{reservationId}/visit")
    public ResponseEntity<ApiResponse<ReservationResponse>> visitReservation(
            @PathVariable UUID storeId,
            @PathVariable UUID reservationId) {
        return ResponseEntity.ok(ApiResponse.success(reservationService.visitReservation(storeId, reservationId)));
    }

    @Operation(summary = "노쇼 처리")
    @PatchMapping("/{reservationId}/no-show")
    public ResponseEntity<ApiResponse<ReservationResponse>> noShowReservation(
            @PathVariable UUID storeId,
            @PathVariable UUID reservationId) {
        return ResponseEntity.ok(ApiResponse.success(reservationService.noShowReservation(storeId, reservationId)));
    }
}
