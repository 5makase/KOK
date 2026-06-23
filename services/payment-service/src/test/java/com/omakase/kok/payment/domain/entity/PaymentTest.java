package com.omakase.kok.payment.domain.entity;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.payment.domain.enums.PaymentStatus;
import com.omakase.kok.payment.domain.exception.PaymentErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Payment 도메인 테스트")
class PaymentTest {

    private UUID reservationId;

    @BeforeEach
    void setUp() {
        reservationId = UUID.randomUUID();
    }

    private Payment buildPendingPayment() {
        return Payment.builder()
                .reservationId(reservationId)
                .amount(10000L)
                .paymentMethod("CARD")
                .build();
    }

    private Payment buildPaidPayment() {
        Payment payment = buildPendingPayment();
        payment.pay();
        return payment;
    }

    @Nested
    @DisplayName("Builder 유효성 검사")
    class Build {

        @Test
        @DisplayName("올바른 값으로 생성 시 PENDING 상태로 초기화된다")
        void success() {
            Payment payment = buildPendingPayment();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getAmount()).isEqualTo(10000L);
            assertThat(payment.getPaymentMethod()).isEqualTo("CARD");
        }

        @Test
        @DisplayName("reservationId가 null이면 IllegalArgumentException이 발생한다")
        void fail_nullReservationId() {
            assertThatThrownBy(() -> Payment.builder()
                    .reservationId(null)
                    .amount(10000L)
                    .paymentMethod("CARD")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("amount가 0이면 IllegalArgumentException이 발생한다")
        void fail_zeroAmount() {
            assertThatThrownBy(() -> Payment.builder()
                    .reservationId(reservationId)
                    .amount(0L)
                    .paymentMethod("CARD")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("amount가 음수이면 IllegalArgumentException이 발생한다")
        void fail_negativeAmount() {
            assertThatThrownBy(() -> Payment.builder()
                    .reservationId(reservationId)
                    .amount(-1000L)
                    .paymentMethod("CARD")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("paymentMethod가 공백이면 IllegalArgumentException이 발생한다")
        void fail_blankPaymentMethod() {
            assertThatThrownBy(() -> Payment.builder()
                    .reservationId(reservationId)
                    .amount(10000L)
                    .paymentMethod("   ")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("paymentMethod가 null이면 IllegalArgumentException이 발생한다")
        void fail_nullPaymentMethod() {
            assertThatThrownBy(() -> Payment.builder()
                    .reservationId(reservationId)
                    .amount(10000L)
                    .paymentMethod(null)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("pay()")
    class Pay {

        @Test
        @DisplayName("PENDING 상태에서 pay() 시 PAID로 전이된다")
        void success() {
            Payment payment = buildPendingPayment();

            payment.pay();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        }

        @Test
        @DisplayName("PAID 상태에서 pay() 시 INVALID_PAYMENT_STATUS 예외가 발생한다")
        void fail_alreadyPaid() {
            Payment payment = buildPaidPayment();

            assertThatThrownBy(payment::pay)
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }

        @Test
        @DisplayName("EXPIRED 상태에서 pay() 시 INVALID_PAYMENT_STATUS 예외가 발생한다")
        void fail_fromExpired() {
            Payment payment = buildPendingPayment();
            payment.expire();

            assertThatThrownBy(payment::pay)
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
    }

    @Nested
    @DisplayName("refund()")
    class Refund {

        @Test
        @DisplayName("전액 환불 시 REFUNDED로 전이되고 refundAmount가 기록된다")
        void success_fullRefund() {
            Payment payment = buildPaidPayment();

            payment.refund(10000L);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(payment.getRefundAmount()).isEqualTo(10000L);
        }

        @Test
        @DisplayName("부분 환불 시 PARTIALLY_REFUNDED로 전이되고 refundAmount가 기록된다")
        void success_partialRefund() {
            Payment payment = buildPaidPayment();

            payment.refund(5000L);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
            assertThat(payment.getRefundAmount()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("PENDING 상태에서 refund() 시 INVALID_PAYMENT_STATUS 예외가 발생한다")
        void fail_fromPending() {
            Payment payment = buildPendingPayment();

            assertThatThrownBy(() -> payment.refund(5000L))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }

        @Test
        @DisplayName("환불 금액이 null이면 INVALID_REFUND_AMOUNT 예외가 발생한다")
        void fail_nullAmount() {
            Payment payment = buildPaidPayment();

            assertThatThrownBy(() -> payment.refund(null))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_REFUND_AMOUNT);
        }

        @Test
        @DisplayName("환불 금액이 0이면 INVALID_REFUND_AMOUNT 예외가 발생한다")
        void fail_zeroAmount() {
            Payment payment = buildPaidPayment();

            assertThatThrownBy(() -> payment.refund(0L))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_REFUND_AMOUNT);
        }

        @Test
        @DisplayName("환불 금액이 결제 금액 초과 시 INVALID_REFUND_AMOUNT 예외가 발생한다")
        void fail_exceedsPaymentAmount() {
            Payment payment = buildPaidPayment();

            assertThatThrownBy(() -> payment.refund(10001L))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_REFUND_AMOUNT);
        }
    }

    @Nested
    @DisplayName("expire()")
    class Expire {

        @Test
        @DisplayName("PENDING 상태에서 expire() 시 EXPIRED로 전이된다")
        void success() {
            Payment payment = buildPendingPayment();

            payment.expire();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        }

        @Test
        @DisplayName("PAID 상태에서 expire() 시 INVALID_PAYMENT_STATUS 예외가 발생한다")
        void fail_fromPaid() {
            Payment payment = buildPaidPayment();

            assertThatThrownBy(payment::expire)
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
    }

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        @Test
        @DisplayName("PENDING 상태에서 cancel() 시 CANCELLED로 전이된다")
        void success() {
            Payment payment = buildPendingPayment();

            payment.cancel();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        }

        @Test
        @DisplayName("PAID 상태에서 cancel() 시 INVALID_PAYMENT_STATUS 예외가 발생한다")
        void fail_fromPaid() {
            Payment payment = buildPaidPayment();

            assertThatThrownBy(payment::cancel)
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }

        @Test
        @DisplayName("EXPIRED 상태에서 cancel() 시 INVALID_PAYMENT_STATUS 예외가 발생한다")
        void fail_fromExpired() {
            Payment payment = buildPendingPayment();
            payment.expire();

            assertThatThrownBy(payment::cancel)
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
    }
}
