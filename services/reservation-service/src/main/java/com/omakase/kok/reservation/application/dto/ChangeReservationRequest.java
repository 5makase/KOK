package com.omakase.kok.reservation.application.dto;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChangeReservationRequest {

    @Min(1)
    private int reservationSize;
}
