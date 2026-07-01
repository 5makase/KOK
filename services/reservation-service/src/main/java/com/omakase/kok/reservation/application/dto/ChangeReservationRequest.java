package com.omakase.kok.reservation.application.dto;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class ChangeReservationRequest {

    @Min(1)
    private Integer reservationSize;

    private UUID newSlotId;
}
