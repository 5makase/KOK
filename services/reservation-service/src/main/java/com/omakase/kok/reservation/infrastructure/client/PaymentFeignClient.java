package com.omakase.kok.reservation.infrastructure.client;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.reservation.infrastructure.client.dto.CreatePaymentRequest;
import com.omakase.kok.reservation.infrastructure.client.dto.PaymentResponse;
import com.omakase.kok.reservation.infrastructure.client.dto.RefundRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@FeignClient(name = "payment-service", path = "/api/v1/internal/payments")
public interface PaymentFeignClient {

    @PostMapping
    ApiResponse<PaymentResponse> createPayment(@RequestBody CreatePaymentRequest request);

    @PostMapping("/{paymentId}/refund")
    ApiResponse<PaymentResponse> refund(@PathVariable("paymentId") UUID paymentId,
                                        @RequestBody RefundRequest request);

    @PostMapping("/{paymentId}/expire")
    ApiResponse<Void> expire(@PathVariable("paymentId") UUID paymentId);

    @GetMapping("/{reservationId}")
    ApiResponse<PaymentResponse> getPayment(@PathVariable("reservationId") UUID reservationId);
}
