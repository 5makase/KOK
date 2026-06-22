package com.omakase.kok.reservation.presentation.controller;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.reservation.application.dto.CancelReservationRequest;
import com.omakase.kok.reservation.application.dto.ReservationResponse;
import com.omakase.kok.reservation.application.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/reservations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class StoreReservationController {

    private final ReservationService reservationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getStoreReservations(
            @PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.success(reservationService.getStoreReservations(storeId)));
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationResponse>> getStoreReservation(
            @PathVariable UUID storeId,
            @PathVariable UUID reservationId) {
        return ResponseEntity.ok(ApiResponse.success(reservationService.getStoreReservation(storeId, reservationId)));
    }

    @PatchMapping("/{reservationId}/cancel")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancelByStore(
            @PathVariable UUID storeId,
            @PathVariable UUID reservationId,
            @RequestBody(required = false) CancelReservationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(reservationService.cancelByStore(storeId, reservationId, request)));
    }

    @PatchMapping("/{reservationId}/visit")
    public ResponseEntity<ApiResponse<ReservationResponse>> visitReservation(
            @PathVariable UUID storeId,
            @PathVariable UUID reservationId) {
        return ResponseEntity.ok(ApiResponse.success(reservationService.visitReservation(storeId, reservationId)));
    }

    @PatchMapping("/{reservationId}/no-show")
    public ResponseEntity<ApiResponse<ReservationResponse>> noShowReservation(
            @PathVariable UUID storeId,
            @PathVariable UUID reservationId) {
        return ResponseEntity.ok(ApiResponse.success(reservationService.noShowReservation(storeId, reservationId)));
    }
}
