package com.omakase.kok.reservation.presentation.controller;

import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.reservation.application.dto.CreateReservationRequest;
import com.omakase.kok.reservation.application.dto.ReservationResponse;
import com.omakase.kok.reservation.application.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ReservationResponse>> createReservation(
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestBody @Valid CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(reservationService.createReservation(request, userId)));
    }
}
