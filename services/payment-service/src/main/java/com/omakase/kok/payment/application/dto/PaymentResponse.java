package com.omakase.kok.payment.application.dto;

import com.omakase.kok.payment.domain.entity.Payment;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class PaymentResponse {

    private UUID paymentId;
    private UUID reservationId;
    private Long amount;
    private String status;
    private Long refundAmount;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .reservationId(payment.getReservationId())
                .amount(payment.getAmount())
                .status(payment.getStatus().name())
                .refundAmount(payment.getRefundAmount())
                .build();
    }
}
