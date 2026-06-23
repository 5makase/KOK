package com.omakase.kok.payment.application.service;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.payment.application.dto.CreatePaymentRequest;
import com.omakase.kok.payment.application.dto.PaymentResponse;
import com.omakase.kok.payment.domain.entity.Payment;
import com.omakase.kok.payment.domain.exception.PaymentErrorCode;
import com.omakase.kok.payment.domain.repository.PaymentRepository;
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
        paymentRepository.findByReservationIdAndDeletedAtIsNull(request.getReservationId())
                .ifPresent(p -> { throw new BaseException(PaymentErrorCode.PAYMENT_ALREADY_EXISTS); });

        Payment payment = Payment.builder()
                .reservationId(request.getReservationId())
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod())
                .build();

        payment.pay();
        paymentRepository.save(payment);

        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse refund(UUID paymentId, Long refundAmount) {
        Payment payment = paymentRepository.findByPaymentIdAndDeletedAtIsNull(paymentId)
                .orElseThrow(() -> new BaseException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        payment.refund(refundAmount);

        return PaymentResponse.from(payment);
    }

    @Transactional
    public void expire(UUID paymentId) {
        Payment payment = paymentRepository.findByPaymentIdAndDeletedAtIsNull(paymentId)
                .orElseThrow(() -> new BaseException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        payment.expire();
    }

    public PaymentResponse getPayment(UUID reservationId) {
        Payment payment = paymentRepository.findByReservationIdAndDeletedAtIsNull(reservationId)
                .orElseThrow(() -> new BaseException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        return PaymentResponse.from(payment);
    }
}
