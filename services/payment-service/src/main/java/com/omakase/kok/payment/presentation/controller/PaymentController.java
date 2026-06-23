package com.kok.payment.presentation.controller;

import com.omakase.kok.common.dto.ApiResponse;
import com.kok.payment.application.dto.CreatePaymentRequest;
import com.kok.payment.application.dto.PaymentResponse;
import com.kok.payment.application.dto.RefundRequest;
import com.kok.payment.application.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @RequestBody @Valid CreatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(paymentService.createPayment(request)));
    }

    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<ApiResponse<PaymentResponse>> refund(
            @PathVariable("paymentId") UUID paymentId,
            @RequestBody @Valid RefundRequest request) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.refund(paymentId, request.getRefundAmount())));
    }

    @PostMapping("/{paymentId}/expire")
    public ResponseEntity<ApiResponse<Void>> expire(
            @PathVariable("paymentId") UUID paymentId) {
        paymentService.expire(paymentId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @PathVariable("reservationId") UUID reservationId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPayment(reservationId)));
    }
}
