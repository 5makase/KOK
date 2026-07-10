package com.omakase.kok.reservation.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class CreateReservationRequest {

    @NotNull
    private UUID slotId;

    @NotBlank
    private String bookerName;

    @NotBlank
    private String bookerPhone;

    @Min(1)
    private int reservationSize;

    private String requestMessage;

    private String paymentMethod;
}
