package com.omakase.kok.reservation.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class CreatePaymentRequest {

    private UUID reservationId;
    private Long amount;
    private String paymentMethod;
}
