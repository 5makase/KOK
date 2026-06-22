package com.omakase.kok.reservation.application.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CancelReservationRequest {

    private String cancelReason;
}
