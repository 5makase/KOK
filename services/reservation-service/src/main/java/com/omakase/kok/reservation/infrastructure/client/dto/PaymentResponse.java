package com.omakase.kok.reservation.infrastructure.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class PaymentResponse {

    private UUID paymentId;
    private UUID reservationId;
    private Long amount;
    private String status;
    private Long refundAmount;
}
