package com.omakase.kok.payment.presentation.controller;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.payment.application.dto.CreatePaymentRequest;
import com.omakase.kok.payment.application.dto.PaymentResponse;
import com.omakase.kok.payment.application.dto.RefundRequest;
import com.omakase.kok.payment.application.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "결제 관리 (내부)")
@RestController
@RequestMapping("/api/v1/internal/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "결제 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @RequestBody @Valid CreatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(paymentService.createPayment(request)));
    }

    @Operation(summary = "결제 환불")
    @PatchMapping("/{paymentId}/refund")
    public ResponseEntity<ApiResponse<PaymentResponse>> refund(
            @PathVariable("paymentId") UUID paymentId,
            @RequestBody @Valid RefundRequest request) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.refund(paymentId, request.getRefundAmount())));
    }

    @Operation(summary = "결제 만료")
    @PatchMapping("/{paymentId}/expire")
    public ResponseEntity<ApiResponse<Void>> expire(
            @PathVariable("paymentId") UUID paymentId) {
        paymentService.expire(paymentId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "결제 조회")
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @PathVariable("reservationId") UUID reservationId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPayment(reservationId)));
    }
}
