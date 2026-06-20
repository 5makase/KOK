package com.kok.payment.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class CreatePaymentRequest {

    @NotNull
    private UUID reservationId;

    @NotNull
    @Min(1)
    private Long amount;

    @NotBlank
    private String paymentMethod;
}
