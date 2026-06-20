package com.kok.payment.application.service;

import com.omakase.kok.common.exception.BaseException;
import com.kok.payment.application.dto.CreatePaymentRequest;
import com.kok.payment.application.dto.PaymentResponse;
import com.kok.payment.domain.entity.Payment;
import com.kok.payment.domain.exception.PaymentErrorCode;
import com.kok.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        Payment payment = Payment.builder()
                .reservationId(request.getReservationId())
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod())
                .build();

        try {
            payment.pay();
        } catch (IllegalStateException e) {
            throw new BaseException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
        paymentRepository.save(payment);

        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse refund(UUID paymentId, Long refundAmount) {
        Payment payment = paymentRepository.findByPaymentIdAndDeletedAtIsNull(paymentId)
                .orElseThrow(() -> new BaseException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        try {
            payment.refund(refundAmount);
        } catch (IllegalStateException e) {
            throw new BaseException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }

        return PaymentResponse.from(payment);
    }

    @Transactional
    public void expire(UUID paymentId) {
        Payment payment = paymentRepository.findByPaymentIdAndDeletedAtIsNull(paymentId)
                .orElseThrow(() -> new BaseException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        try {
            payment.expire();
        } catch (IllegalStateException e) {
            throw new BaseException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
    }

    public PaymentResponse getPayment(UUID reservationId) {
        Payment payment = paymentRepository.findByReservationIdAndDeletedAtIsNull(reservationId)
                .orElseThrow(() -> new BaseException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        return PaymentResponse.from(payment);
    }
}
