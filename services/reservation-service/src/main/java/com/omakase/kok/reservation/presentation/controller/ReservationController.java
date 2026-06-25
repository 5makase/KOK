package com.omakase.kok.reservation.presentation.controller;

import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.reservation.application.dto.CancelReservationRequest;
import com.omakase.kok.reservation.application.dto.ChangeReservationRequest;
import com.omakase.kok.reservation.application.dto.CreateReservationRequest;
import com.omakase.kok.reservation.application.dto.ReservationResponse;
import com.omakase.kok.reservation.application.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "예약 관리 (사용자)")
@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "예약 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponse>> createReservation(
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestBody @Valid CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(reservationService.createReservation(request, userId)));
    }

    @Operation(summary = "내 예약 목록 조회")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getMyReservations(
            @RequestHeader(AuthConstants.USER_ID) UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(reservationService.getMyReservations(userId)));
    }

    @Operation(summary = "내 예약 단건 조회")
    @GetMapping("/me/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationResponse>> getMyReservation(
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @PathVariable UUID reservationId) {
        return ResponseEntity.ok(ApiResponse.success(reservationService.getMyReservation(reservationId, userId)));
    }

    @Operation(summary = "예약 변경")
    @PatchMapping("/{reservationId}/change")
    public ResponseEntity<ApiResponse<ReservationResponse>> changeReservation(
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @PathVariable UUID reservationId,
            @RequestBody @Valid ChangeReservationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(reservationService.changeReservation(reservationId, userId, request)));
    }

    @Operation(summary = "예약 취소")
    @PatchMapping("/{reservationId}/cancel")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancelReservation(
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @PathVariable UUID reservationId,
            @RequestBody(required = false) CancelReservationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(reservationService.cancelReservation(reservationId, userId, request)));
    }
}
