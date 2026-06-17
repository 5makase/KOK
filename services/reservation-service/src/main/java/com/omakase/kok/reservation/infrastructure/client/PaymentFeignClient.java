package com.omakase.kok.reservation.infrastructure.client;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.reservation.infrastructure.client.dto.CreatePaymentRequest;
import com.omakase.kok.reservation.infrastructure.client.dto.PaymentResponse;
import com.omakase.kok.reservation.infrastructure.client.dto.RefundRequest;
import com.omakase.kok.reservation.infrastructure.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@FeignClient(name = "payment-service", path = "/api/v1/internal/payments", configuration = FeignConfig.class)
public interface PaymentFeignClient {

    @PostMapping
    ApiResponse<PaymentResponse> createPayment(@RequestBody CreatePaymentRequest request);

    @PostMapping("/{paymentId}/refund")
    ApiResponse<PaymentResponse> refund(@PathVariable UUID paymentId,
                                        @RequestBody RefundRequest request);

    @PostMapping("/{paymentId}/expire")
    ApiResponse<Void> expire(@PathVariable UUID paymentId);

    @GetMapping("/{reservationId}")
    ApiResponse<PaymentResponse> getPayment(@PathVariable UUID reservationId);
}
