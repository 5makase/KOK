package com.omakase.kok.payment.application.service;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.payment.application.dto.CreatePaymentRequest;
import com.omakase.kok.payment.application.dto.PaymentResponse;
import com.omakase.kok.payment.domain.entity.Payment;
import com.omakase.kok.payment.domain.enums.PaymentStatus;
import com.omakase.kok.payment.domain.exception.PaymentErrorCode;
import com.omakase.kok.payment.domain.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService 단위 테스트")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository);
    }

    private Payment buildPayment(UUID reservationId, Long amount) {
        Payment payment = Payment.builder()
                .reservationId(reservationId)
                .amount(amount)
                .paymentMethod("CARD")
                .build();
        ReflectionTestUtils.setField(payment, "paymentId", UUID.randomUUID());
        return payment;
    }

    private CreatePaymentRequest buildRequest(UUID reservationId, Long amount) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        ReflectionTestUtils.setField(request, "reservationId", reservationId);
        ReflectionTestUtils.setField(request, "amount", amount);
        ReflectionTestUtils.setField(request, "paymentMethod", "CARD");
        return request;
    }

    @Nested
    @DisplayName("createPayment()")
    class CreatePayment {

        @Test
        @DisplayName("중복 결제가 없으면 결제를 생성하고 PAID 상태를 반환한다")
        void success() {
            UUID reservationId = UUID.randomUUID();
            CreatePaymentRequest request = buildRequest(reservationId, 10000L);

            when(paymentRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.empty());
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                ReflectionTestUtils.setField(p, "paymentId", UUID.randomUUID());
                return p;
            });

            PaymentResponse response = paymentService.createPayment(request);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.PAID.name());
            assertThat(response.getAmount()).isEqualTo(10000L);
            verify(paymentRepository).save(any(Payment.class));
        }

        @Test
        @DisplayName("같은 reservationId로 결제가 이미 존재하면 PAYMENT_ALREADY_EXISTS 예외가 발생한다")
        void fail_duplicatePayment() {
            UUID reservationId = UUID.randomUUID();
            CreatePaymentRequest request = buildRequest(reservationId, 10000L);
            Payment existingPayment = buildPayment(reservationId, 10000L);

            when(paymentRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(existingPayment));

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_ALREADY_EXISTS);

            verify(paymentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("refund()")
    class Refund {

        @Test
        @DisplayName("전액 환불 시 REFUNDED 상태를 반환한다")
        void success_fullRefund() {
            UUID paymentId = UUID.randomUUID();
            Payment payment = buildPayment(UUID.randomUUID(), 10000L);
            payment.pay();

            when(paymentRepository.findByPaymentIdAndDeletedAtIsNull(paymentId))
                    .thenReturn(Optional.of(payment));

            PaymentResponse response = paymentService.refund(paymentId, 10000L);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.REFUNDED.name());
            assertThat(response.getRefundAmount()).isEqualTo(10000L);
        }

        @Test
        @DisplayName("부분 환불 시 PARTIALLY_REFUNDED 상태를 반환한다")
        void success_partialRefund() {
            UUID paymentId = UUID.randomUUID();
            Payment payment = buildPayment(UUID.randomUUID(), 10000L);
            payment.pay();

            when(paymentRepository.findByPaymentIdAndDeletedAtIsNull(paymentId))
                    .thenReturn(Optional.of(payment));

            PaymentResponse response = paymentService.refund(paymentId, 3000L);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED.name());
            assertThat(response.getRefundAmount()).isEqualTo(3000L);
        }

        @Test
        @DisplayName("존재하지 않는 paymentId이면 PAYMENT_NOT_FOUND 예외가 발생한다")
        void fail_notFound() {
            UUID paymentId = UUID.randomUUID();
            when(paymentRepository.findByPaymentIdAndDeletedAtIsNull(paymentId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.refund(paymentId, 5000L))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("expire()")
    class Expire {

        @Test
        @DisplayName("PENDING 결제를 EXPIRED로 처리한다")
        void success() {
            UUID paymentId = UUID.randomUUID();
            Payment payment = buildPayment(UUID.randomUUID(), 10000L);

            when(paymentRepository.findByPaymentIdAndDeletedAtIsNull(paymentId))
                    .thenReturn(Optional.of(payment));

            paymentService.expire(paymentId);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        }

        @Test
        @DisplayName("존재하지 않는 paymentId이면 PAYMENT_NOT_FOUND 예외가 발생한다")
        void fail_notFound() {
            UUID paymentId = UUID.randomUUID();
            when(paymentRepository.findByPaymentIdAndDeletedAtIsNull(paymentId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.expire(paymentId))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getPayment()")
    class GetPayment {

        @Test
        @DisplayName("reservationId로 결제 정보를 조회한다")
        void success() {
            UUID reservationId = UUID.randomUUID();
            Payment payment = buildPayment(reservationId, 10000L);

            when(paymentRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(payment));

            PaymentResponse response = paymentService.getPayment(reservationId);

            assertThat(response.getAmount()).isEqualTo(10000L);
        }

        @Test
        @DisplayName("존재하지 않는 reservationId이면 PAYMENT_NOT_FOUND 예외가 발생한다")
        void fail_notFound() {
            UUID reservationId = UUID.randomUUID();
            when(paymentRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPayment(reservationId))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
    }
}
