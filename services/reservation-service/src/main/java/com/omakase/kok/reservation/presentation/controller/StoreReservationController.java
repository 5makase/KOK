package com.omakase.kok.reservation.presentation.controller;

import com.omakase.kok.common.dto.ApiResponse;
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
}
