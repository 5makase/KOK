package com.omakase.kok.reservation.infrastructure.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.reservation.domain.entity.Reservation;
import com.omakase.kok.reservation.domain.entity.ReservationSlot;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import com.omakase.kok.reservation.domain.repository.ReservationOutboxEventRepository;
import com.omakase.kok.reservation.domain.repository.ReservationRepository;
import com.omakase.kok.reservation.infrastructure.client.PaymentFeignClient;
import com.omakase.kok.reservation.infrastructure.client.dto.PaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PendingExpirationScheduler 단위 테스트")
class PendingExpirationSchedulerTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationOutboxEventRepository outboxEventRepository;
    @Mock private PaymentFeignClient paymentFeignClient;
    @Mock private RedissonClient redissonClient;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private RLock rLock;
    @Mock private RAtomicLong atomicLong;

    private PendingExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        scheduler = new PendingExpirationScheduler(
                reservationRepository,
                outboxEventRepository,
                paymentFeignClient,
                redissonClient,
                transactionManager,
                objectMapper
        );
    }

    private Reservation buildPendingReservation() {
        UUID slotId = UUID.randomUUID();
        Reservation r = Reservation.builder()
                .slotId(slotId)
                .userId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .bookerName("홍길동")
                .bookerPhone("010-1234-5678")
                .reservationSize(2)
                .build();
        ReflectionTestUtils.setField(r, "reservationId", UUID.randomUUID());
        return r;
    }

    private void givenLockAcquired() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    private void givenTransactionExecutes() {
        TransactionStatus txStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(txStatus);
    }

    @Nested
    @DisplayName("expirePendingReservations()")
    class ExpirePendingReservations {

        @Test
        @DisplayName("분산락 획득 실패 시 아무런 처리도 하지 않는다")
        void skip_whenLockNotAcquired() throws Exception {
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);
            when(rLock.isHeldByCurrentThread()).thenReturn(false);

            scheduler.expirePendingReservations();

            verify(reservationRepository, never()).findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(any(), any());
        }

        @Test
        @DisplayName("PENDING 예약이 없으면 처리하지 않는다")
        void skip_whenNoTargets() throws Exception {
            givenLockAcquired();
            when(reservationRepository.findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
                    eq(ReservationStatus.PAYMENT_PENDING), any(LocalDateTime.class)))
                    .thenReturn(List.of());

            scheduler.expirePendingReservations();

            verify(paymentFeignClient, never()).getPayment(any());
        }

        @Test
        @DisplayName("이미 PAYMENT_PENDING이 아닌 예약은 취소하지 않고 Redis 복구도 하지 않는다")
        void skip_whenAlreadyNotPending() throws Exception {
            Reservation reservation = buildPendingReservation();
            reservation.confirm();

            givenLockAcquired();
            when(reservationRepository.findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
                    eq(ReservationStatus.PAYMENT_PENDING), any(LocalDateTime.class)))
                    .thenReturn(List.of(reservation));

            PaymentResponse paymentResp = new PaymentResponse();
            ReflectionTestUtils.setField(paymentResp, "paymentId", UUID.randomUUID());
            ApiResponse<PaymentResponse> apiResp = ApiResponse.success(paymentResp);
            when(paymentFeignClient.getPayment(any())).thenReturn(apiResp);
            when(paymentFeignClient.expire(any())).thenReturn(ApiResponse.success());

            givenTransactionExecutes();
            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(any()))
                    .thenReturn(Optional.of(reservation));

            scheduler.expirePendingReservations();

            verify(redissonClient, never()).getAtomicLong(anyString());
        }

        @Test
        @DisplayName("PAYMENT_PENDING 예약을 만료 처리하고 Redis 잔여 인원을 복구한다")
        void success_expireAndRestoreRedis() throws Exception {
            Reservation reservation = buildPendingReservation();
            UUID reservationId = reservation.getReservationId();
            UUID slotId = reservation.getSlotId();

            givenLockAcquired();
            when(reservationRepository.findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
                    eq(ReservationStatus.PAYMENT_PENDING), any(LocalDateTime.class)))
                    .thenReturn(List.of(reservation));

            PaymentResponse paymentResp = new PaymentResponse();
            ReflectionTestUtils.setField(paymentResp, "paymentId", UUID.randomUUID());
            ApiResponse<PaymentResponse> apiResp = ApiResponse.success(paymentResp);
            when(paymentFeignClient.getPayment(reservationId)).thenReturn(apiResp);
            when(paymentFeignClient.expire(any())).thenReturn(ApiResponse.success());

            givenTransactionExecutes();
            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(reservation));
            when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(redissonClient.getAtomicLong(contains(slotId.toString()))).thenReturn(atomicLong);

            scheduler.expirePendingReservations();

            verify(atomicLong).addAndGet(2L);
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        }

        @Test
        @DisplayName("Payment 만료 처리 실패해도 예약 취소는 계속 진행된다")
        void continueOnPaymentExpireFailure() throws Exception {
            Reservation reservation = buildPendingReservation();
            UUID reservationId = reservation.getReservationId();

            givenLockAcquired();
            when(reservationRepository.findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
                    eq(ReservationStatus.PAYMENT_PENDING), any(LocalDateTime.class)))
                    .thenReturn(List.of(reservation));

            // Payment 만료 처리 실패
            when(paymentFeignClient.getPayment(reservationId))
                    .thenThrow(new RuntimeException("payment service unavailable"));

            givenTransactionExecutes();
            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(reservation));
            when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(redissonClient.getAtomicLong(anyString())).thenReturn(atomicLong);

            // 예외 없이 정상 종료되어야 함
            assertThatCode(() -> scheduler.expirePendingReservations()).doesNotThrowAnyException();

            // 예약 취소는 진행됨
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        }

        @Test
        @DisplayName("특정 예약 처리 실패 시 다른 예약 처리는 계속된다")
        void continueOnIndividualFailure() throws Exception {
            Reservation r1 = buildPendingReservation();
            Reservation r2 = buildPendingReservation();

            givenLockAcquired();
            when(reservationRepository.findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
                    eq(ReservationStatus.PAYMENT_PENDING), any(LocalDateTime.class)))
                    .thenReturn(List.of(r1, r2));

            // r1 처리 실패 (payment 조회 예외)
            when(paymentFeignClient.getPayment(r1.getReservationId()))
                    .thenThrow(new RuntimeException("r1 실패"));

            // r2 처리 성공
            PaymentResponse p2Resp = new PaymentResponse();
            ReflectionTestUtils.setField(p2Resp, "paymentId", UUID.randomUUID());
            ApiResponse<PaymentResponse> apiResp2 = ApiResponse.success(p2Resp);
            when(paymentFeignClient.getPayment(r2.getReservationId())).thenReturn(apiResp2);
            when(paymentFeignClient.expire(any())).thenReturn(ApiResponse.success());

            givenTransactionExecutes();
            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(any()))
                    .thenAnswer(inv -> {
                        UUID id = inv.getArgument(0);
                        if (id.equals(r1.getReservationId())) return Optional.of(r1);
                        if (id.equals(r2.getReservationId())) return Optional.of(r2);
                        return Optional.empty();
                    });
            when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(redissonClient.getAtomicLong(anyString())).thenReturn(atomicLong);

            assertThatCode(() -> scheduler.expirePendingReservations()).doesNotThrowAnyException();

            // r2는 취소되어야 함
            assertThat(r2.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        }
    }
}
