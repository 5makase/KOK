package com.kok.payment.domain.entity;

import com.omakase.kok.common.entity.BaseEntity;
import com.kok.payment.domain.enums.PaymentStatus;
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
        this.reservationId = reservationId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = PaymentStatus.PENDING;
    }

    public void pay() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 결제 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.PAID;
    }

    public void refund(Long refundAmount) {
        if (this.status != PaymentStatus.PAID) {
            throw new IllegalStateException("PAID 상태에서만 환불 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        this.refundAmount = refundAmount;
        this.status = refundAmount.equals(this.amount)
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED;
    }

    public void expire() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 만료 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.EXPIRED;
    }

    public void cancel() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 취소 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.CANCELLED;
    }
}
