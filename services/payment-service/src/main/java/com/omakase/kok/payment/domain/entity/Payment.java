package com.omakase.kok.payment.domain.entity;

import com.omakase.kok.common.entity.BaseEntity;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.payment.domain.enums.PaymentStatus;
import com.omakase.kok.payment.domain.exception.PaymentErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payment_id", updatable = false, nullable = false)
    private UUID paymentId;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "refund_amount")
    private Long refundAmount;

    @Builder
    public Payment(UUID reservationId, Long amount, String paymentMethod) {
        if (reservationId == null) {
            throw new IllegalArgumentException("reservationId는 필수입니다.");
        }
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        }
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new IllegalArgumentException("결제 수단은 필수입니다.");
        }
        this.reservationId = reservationId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = PaymentStatus.PENDING;
    }

    public void pay() {
        if (this.status != PaymentStatus.PENDING) {
            throw new BaseException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
        this.status = PaymentStatus.PAID;
    }

    public void refund(Long refundAmount) {
        if (this.status != PaymentStatus.PAID) {
            throw new BaseException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
        if (refundAmount == null || refundAmount <= 0 || refundAmount > this.amount) {
            throw new BaseException(PaymentErrorCode.INVALID_REFUND_AMOUNT);
        }
        this.refundAmount = refundAmount;
        this.status = refundAmount.equals(this.amount)
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED;
    }

    public void expire() {
        if (this.status != PaymentStatus.PENDING) {
            throw new BaseException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
        this.status = PaymentStatus.EXPIRED;
    }

    public void cancel() {
        if (this.status != PaymentStatus.PENDING) {
            throw new BaseException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
        this.status = PaymentStatus.CANCELLED;
    }
}
