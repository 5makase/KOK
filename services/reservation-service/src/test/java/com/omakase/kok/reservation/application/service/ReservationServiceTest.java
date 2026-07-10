package com.omakase.kok.reservation.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.reservation.application.dto.CancelReservationRequest;
import com.omakase.kok.reservation.application.dto.ChangeReservationRequest;
import com.omakase.kok.reservation.application.dto.CreateReservationRequest;
import com.omakase.kok.reservation.application.dto.ReservationResponse;
import com.omakase.kok.reservation.domain.entity.Reservation;
import com.omakase.kok.reservation.domain.entity.ReservationIdempotencyKey;
import com.omakase.kok.reservation.domain.entity.ReservationSlot;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import com.omakase.kok.reservation.domain.enums.SlotStatus;
import com.omakase.kok.reservation.domain.exception.ReservationErrorCode;
import com.omakase.kok.reservation.domain.exception.SlotErrorCode;
import com.omakase.kok.reservation.domain.repository.ReservationIdempotencyKeyRepository;
import com.omakase.kok.reservation.domain.repository.ReservationOutboxEventRepository;
import com.omakase.kok.reservation.domain.repository.ReservationRepository;
import com.omakase.kok.reservation.domain.repository.ReservationSlotRepository;
import com.omakase.kok.reservation.infrastructure.client.PaymentFeignClient;
import com.omakase.kok.reservation.infrastructure.client.dto.PaymentResponse;
import com.omakase.kok.reservation.infrastructure.client.dto.RefundRequest;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
@DisplayName("ReservationService 단위 테스트")
class ReservationServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationSlotRepository slotRepository;
    @Mock private ReservationOutboxEventRepository outboxEventRepository;
    @Mock private ReservationIdempotencyKeyRepository idempotencyKeyRepository;
    @Mock private RedissonClient redissonClient;
    @Mock private PaymentFeignClient paymentFeignClient;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private RLock rLock;
    @Mock private RAtomicLong atomicLong;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        reservationService = new ReservationService(
                reservationRepository,
                slotRepository,
                outboxEventRepository,
                idempotencyKeyRepository,
                redissonClient,
                paymentFeignClient,
                transactionManager,
                objectMapper
        );
    }

    private ReservationSlot buildOpenSlot(UUID storeId, boolean depositRequired) {
        ReservationSlot slot = ReservationSlot.builder()
                .storeId(storeId)
                .slotDate(LocalDate.now().plusDays(5))
                .slotTime(LocalTime.of(18, 0))
                .maxCapacity(4)
                .depositRequired(depositRequired)
                .depositAmount(depositRequired ? 10000L : null)
                .build();
        ReflectionTestUtils.setField(slot, "slotId", UUID.randomUUID());
        return slot;
    }

    private Reservation buildReservation(UUID slotId, UUID userId, UUID storeId) {
        Reservation r = Reservation.builder()
                .slotId(slotId)
                .userId(userId)
                .storeId(storeId)
                .bookerName("홍길동")
                .bookerPhone("010-1234-5678")
                .reservationSize(2)
                .build();
        ReflectionTestUtils.setField(r, "reservationId", UUID.randomUUID());
        return r;
    }

    private CreateReservationRequest buildCreateRequest(UUID slotId, int size, String paymentMethod) {
        CreateReservationRequest req = new CreateReservationRequest();
        ReflectionTestUtils.setField(req, "slotId", slotId);
        ReflectionTestUtils.setField(req, "bookerName", "홍길동");
        ReflectionTestUtils.setField(req, "bookerPhone", "010-1234-5678");
        ReflectionTestUtils.setField(req, "reservationSize", size);
        ReflectionTestUtils.setField(req, "paymentMethod", paymentMethod);
        return req;
    }

    private void givenLockAcquired() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    private Reservation buildConfirmedReservation(UUID slotId, UUID userId, UUID storeId,
                                                   LocalDateTime scheduledAt, int size) {
        Reservation r = Reservation.builder()
                .slotId(slotId)
                .userId(userId)
                .storeId(storeId)
                .scheduledAt(scheduledAt)
                .bookerName("홍길동")
                .bookerPhone("010-1234-5678")
                .reservationSize(size)
                .build();
        ReflectionTestUtils.setField(r, "reservationId", UUID.randomUUID());
        ReflectionTestUtils.setField(r, "status", ReservationStatus.CONFIRMED);
        return r;
    }

    private ChangeReservationRequest buildChangeRequest(Integer reservationSize, UUID newSlotId) {
        ChangeReservationRequest req = new ChangeReservationRequest();
        ReflectionTestUtils.setField(req, "reservationSize", reservationSize);
        ReflectionTestUtils.setField(req, "newSlotId", newSlotId);
        return req;
    }

    private RLock lockFor(String key) throws InterruptedException {
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock(key)).thenReturn(lock);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        return lock;
    }

    private RAtomicLong atomicLongFor(String key) {
        RAtomicLong atomic = mock(RAtomicLong.class);
        when(redissonClient.getAtomicLong(key)).thenReturn(atomic);
        return atomic;
    }

    private ReservationSlot buildSlot(UUID storeId, LocalDate slotDate, LocalTime slotTime, int maxCapacity) {
        ReservationSlot slot = ReservationSlot.builder()
                .storeId(storeId)
                .slotDate(slotDate)
                .slotTime(slotTime)
                .maxCapacity(maxCapacity)
                .depositRequired(false)
                .depositAmount(null)
                .build();
        ReflectionTestUtils.setField(slot, "slotId", UUID.randomUUID());
        return slot;
    }

    private void givenTransactionExecutes() {
        TransactionStatus txStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(txStatus);
    }

    @Nested
    @DisplayName("createReservation()")
    class CreateReservation {

        @Test
        @DisplayName("예약금 불필요 슬롯: 정상 예약 시 즉시 CONFIRMED 상태로 반환된다")
        void success_noDeposit() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID storeId = UUID.randomUUID();
            ReservationSlot slot = buildOpenSlot(storeId, false);
            UUID slotId = slot.getSlotId();
            String idempotencyKey = UUID.randomUUID().toString();

            when(slotRepository.findBySlotIdAndDeletedAtIsNull(slotId))
                    .thenReturn(Optional.of(slot));
            givenLockAcquired();
            when(redissonClient.getAtomicLong(anyString())).thenReturn(atomicLong);
            when(atomicLong.addAndGet(-2L)).thenReturn(2L);
            givenTransactionExecutes();
            when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
                Reservation r = inv.getArgument(0);
                ReflectionTestUtils.setField(r, "reservationId", UUID.randomUUID());
                return r;
            });
            when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateReservationRequest request = buildCreateRequest(slotId, 2, null);
            ReservationResponse response = reservationService.createReservation(request, userId, idempotencyKey);

            assertThat(response.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
            verify(reservationRepository).save(any(Reservation.class));
            verify(outboxEventRepository).save(any());
            verify(idempotencyKeyRepository).save(any(ReservationIdempotencyKey.class));
            verify(paymentFeignClient, never()).createPayment(any());
        }

        @Test
        @DisplayName("이미 처리된 Idempotency-Key로 재요청하면 실제 로직 없이 기존 응답을 그대로 반환한다")
        void success_returnsExistingResponseForSameIdempotencyKey() {
            UUID userId = UUID.randomUUID();
            UUID storeId = UUID.randomUUID();
            String idempotencyKey = UUID.randomUUID().toString();
            Reservation existingReservation = buildConfirmedReservation(
                    UUID.randomUUID(), userId, storeId, LocalDateTime.now().plusDays(5), 2);

            ReservationIdempotencyKey mapping = ReservationIdempotencyKey.builder()
                    .userId(userId).idempotencyKey(idempotencyKey).reservationId(existingReservation.getReservationId())
                    .build();
            when(idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
                    .thenReturn(Optional.of(mapping));
            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(existingReservation.getReservationId()))
                    .thenReturn(Optional.of(existingReservation));

            CreateReservationRequest request = buildCreateRequest(UUID.randomUUID(), 2, null);
            ReservationResponse response = reservationService.createReservation(request, userId, idempotencyKey);

            assertThat(response.getReservationId()).isEqualTo(existingReservation.getReservationId());
            verifyNoInteractions(slotRepository);
            verify(redissonClient, never()).getLock(anyString());
        }

        @Test
        @DisplayName("같은 Idempotency-Key로 처리 중인 요청이 있으면 IDEMPOTENCY_KEY_IN_PROGRESS 예외가 발생한다")
        void fail_idempotencyKeyInProgress() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID storeId = UUID.randomUUID();
            String idempotencyKey = UUID.randomUUID().toString();
            ReservationSlot slot = buildOpenSlot(storeId, false);

            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(0, TimeUnit.SECONDS)).thenReturn(false);
            when(rLock.isHeldByCurrentThread()).thenReturn(false);

            CreateReservationRequest request = buildCreateRequest(slot.getSlotId(), 2, null);

            assertThatThrownBy(() -> reservationService.createReservation(request, userId, idempotencyKey))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.IDEMPOTENCY_KEY_IN_PROGRESS);

            verifyNoInteractions(slotRepository);
        }

        @Test
        @DisplayName("슬롯이 존재하지 않으면 SLOT_NOT_FOUND 예외가 발생한다")
        void fail_slotNotFound() throws Exception {
            UUID slotId = UUID.randomUUID();
            when(slotRepository.findBySlotIdAndDeletedAtIsNull(slotId))
                    .thenReturn(Optional.empty());
            givenLockAcquired();

            CreateReservationRequest request = buildCreateRequest(slotId, 2, null);

            assertThatThrownBy(() -> reservationService.createReservation(request, UUID.randomUUID(), UUID.randomUUID().toString()))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(SlotErrorCode.SLOT_NOT_FOUND);

            // 아무 것도 생성되지 않았으므로 idempotency 매핑도 남지 않는다
            verify(idempotencyKeyRepository, never()).save(any());
        }

        @Test
        @DisplayName("슬롯이 OPEN 상태가 아니면 SLOT_UNAVAILABLE 예외가 발생하고 idempotency 매핑이 남지 않는다")
        void fail_slotUnavailable() throws Exception {
            UUID storeId = UUID.randomUUID();
            ReservationSlot slot = buildOpenSlot(storeId, false);
            ReflectionTestUtils.setField(slot, "status", SlotStatus.CLOSED);
            UUID slotId = slot.getSlotId();

            when(slotRepository.findBySlotIdAndDeletedAtIsNull(slotId))
                    .thenReturn(Optional.of(slot));
            givenLockAcquired();

            CreateReservationRequest request = buildCreateRequest(slotId, 2, null);

            assertThatThrownBy(() -> reservationService.createReservation(request, UUID.randomUUID(), UUID.randomUUID().toString()))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.SLOT_UNAVAILABLE);

            verify(idempotencyKeyRepository, never()).save(any());
        }

        @Test
        @DisplayName("분산락 획득 실패 시 SLOT_LOCK_FAILED 예외가 발생한다")
        void fail_lockFailed() throws Exception {
            UUID userId = UUID.randomUUID();
            String idempotencyKey = UUID.randomUUID().toString();
            UUID storeId = UUID.randomUUID();
            ReservationSlot slot = buildOpenSlot(storeId, false);
            UUID slotId = slot.getSlotId();

            when(slotRepository.findBySlotIdAndDeletedAtIsNull(slotId))
                    .thenReturn(Optional.of(slot));
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(false);
            when(rLock.isHeldByCurrentThread()).thenReturn(false);
            // idempotency 락은 성공시켜, 검증하려는 slot 락 실패 경로를 타도록 한다
            lockFor("reservation:idempotency:lock:" + userId + ":" + idempotencyKey);

            CreateReservationRequest request = buildCreateRequest(slotId, 2, null);

            assertThatThrownBy(() -> reservationService.createReservation(request, userId, idempotencyKey))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.SLOT_LOCK_FAILED);
        }

        @Test
        @DisplayName("Redis 잔여 인원 부족 시 SLOT_CAPACITY_EXCEEDED 예외가 발생하고 Redis가 복구된다")
        void fail_capacityExceeded() throws Exception {
            UUID storeId = UUID.randomUUID();
            // maxCapacity=1: DB도 요청 인원(2)보다 부족해 재동기화 없이 즉시 예외 발생
            ReservationSlot slot = ReservationSlot.builder()
                    .storeId(storeId)
                    .slotDate(LocalDate.now().plusDays(5))
                    .slotTime(LocalTime.of(18, 0))
                    .maxCapacity(1)
                    .depositRequired(false)
                    .depositAmount(null)
                    .build();
            ReflectionTestUtils.setField(slot, "slotId", UUID.randomUUID());
            UUID slotId = slot.getSlotId();

            when(slotRepository.findBySlotIdAndDeletedAtIsNull(slotId))
                    .thenReturn(Optional.of(slot));
            givenLockAcquired();
            when(redissonClient.getAtomicLong(anyString())).thenReturn(atomicLong);
            when(atomicLong.addAndGet(-2L)).thenReturn(-1L);

            CreateReservationRequest request = buildCreateRequest(slotId, 2, null);

            assertThatThrownBy(() -> reservationService.createReservation(request, UUID.randomUUID(), UUID.randomUUID().toString()))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.SLOT_CAPACITY_EXCEEDED);

            verify(atomicLong).addAndGet(2L);
            verify(idempotencyKeyRepository, never()).save(any());
        }

        @Test
        @DisplayName("예약금 필요 슬롯에서 paymentMethod 없으면 PAYMENT_METHOD_REQUIRED 예외가 발생한다")
        void fail_paymentMethodRequired() throws Exception {
            UUID storeId = UUID.randomUUID();
            ReservationSlot slot = buildOpenSlot(storeId, true);
            UUID slotId = slot.getSlotId();

            when(slotRepository.findBySlotIdAndDeletedAtIsNull(slotId))
                    .thenReturn(Optional.of(slot));
            givenLockAcquired();

            CreateReservationRequest request = buildCreateRequest(slotId, 2, null);

            assertThatThrownBy(() -> reservationService.createReservation(request, UUID.randomUUID(), UUID.randomUUID().toString()))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.PAYMENT_METHOD_REQUIRED);
        }
    }

    @Nested
    @DisplayName("cancelReservation()")
    class CancelReservation {

        @Test
        @DisplayName("방문 3일 이상 전 취소 시 전액 환불이 요청된다")
        void success_fullRefund_threeDaysAhead() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID storeId = UUID.randomUUID();
            UUID reservationId = UUID.randomUUID();

            ReservationSlot slot = ReservationSlot.builder()
                    .storeId(storeId)
                    .slotDate(LocalDate.now().plusDays(5))
                    .slotTime(LocalTime.of(18, 0))
                    .maxCapacity(4)
                    .depositRequired(true)
                    .depositAmount(10000L)
                    .build();
            ReflectionTestUtils.setField(slot, "slotId", UUID.randomUUID());

            Reservation reservation = buildReservation(slot.getSlotId(), userId, storeId);
            ReflectionTestUtils.setField(reservation, "reservationId", reservationId);

            UUID paymentId = UUID.randomUUID();
            PaymentResponse paymentResponse = new PaymentResponse();
            ReflectionTestUtils.setField(paymentResponse, "paymentId", paymentId);
            ReflectionTestUtils.setField(paymentResponse, "amount", 10000L);
            ApiResponse<PaymentResponse> apiResponse = ApiResponse.success(paymentResponse);

            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(reservation));
            when(slotRepository.findBySlotIdAndDeletedAtIsNull(any()))
                    .thenReturn(Optional.of(slot));
            when(paymentFeignClient.getPayment(reservationId)).thenReturn(apiResponse);
            givenTransactionExecutes();
            when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            givenLockAcquired();
            when(redissonClient.getAtomicLong(anyString())).thenReturn(atomicLong);

            CancelReservationRequest cancelRequest = new CancelReservationRequest();
            ReflectionTestUtils.setField(cancelRequest, "cancelReason", "단순 변심");

            reservationService.cancelReservation(reservationId, userId, cancelRequest);

            verify(paymentFeignClient).refund(eq(paymentId), argThat(r -> r.getRefundAmount().equals(10000L)));
        }

        @Test
        @DisplayName("방문 1~2일 전 취소 시 50% 환불이 요청된다")
        void success_halfRefund_twoDaysAhead() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID storeId = UUID.randomUUID();
            UUID reservationId = UUID.randomUUID();

            ReservationSlot slot = ReservationSlot.builder()
                    .storeId(storeId)
                    .slotDate(LocalDate.now().plusDays(2))
                    .slotTime(LocalTime.of(18, 0))
                    .maxCapacity(4)
                    .depositRequired(true)
                    .depositAmount(10000L)
                    .build();
            ReflectionTestUtils.setField(slot, "slotId", UUID.randomUUID());

            Reservation reservation = buildReservation(slot.getSlotId(), userId, storeId);
            ReflectionTestUtils.setField(reservation, "reservationId", reservationId);

            UUID paymentId = UUID.randomUUID();
            PaymentResponse paymentResponse = new PaymentResponse();
            ReflectionTestUtils.setField(paymentResponse, "paymentId", paymentId);
            ReflectionTestUtils.setField(paymentResponse, "amount", 10000L);
            ApiResponse<PaymentResponse> apiResponse = ApiResponse.success(paymentResponse);

            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(reservation));
            when(slotRepository.findBySlotIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(slot));
            when(paymentFeignClient.getPayment(reservationId)).thenReturn(apiResponse);
            givenTransactionExecutes();
            when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            givenLockAcquired();
            when(redissonClient.getAtomicLong(anyString())).thenReturn(atomicLong);

            reservationService.cancelReservation(reservationId, userId, null);

            verify(paymentFeignClient).refund(eq(paymentId), argThat(r -> r.getRefundAmount().equals(5000L)));
        }

        @Test
        @DisplayName("방문 당일 취소 시 환불이 요청되지 않는다")
        void success_noRefund_sameDay() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID storeId = UUID.randomUUID();
            UUID reservationId = UUID.randomUUID();

            ReservationSlot slot = ReservationSlot.builder()
                    .storeId(storeId)
                    .slotDate(LocalDate.now())
                    .slotTime(LocalTime.of(18, 0))
                    .maxCapacity(4)
                    .depositRequired(true)
                    .depositAmount(10000L)
                    .build();
            ReflectionTestUtils.setField(slot, "slotId", UUID.randomUUID());

            Reservation reservation = buildReservation(slot.getSlotId(), userId, storeId);
            ReflectionTestUtils.setField(reservation, "reservationId", reservationId);

            PaymentResponse paymentResponse = new PaymentResponse();
            ReflectionTestUtils.setField(paymentResponse, "paymentId", UUID.randomUUID());
            ReflectionTestUtils.setField(paymentResponse, "amount", 10000L);
            ApiResponse<PaymentResponse> apiResponse = ApiResponse.success(paymentResponse);

            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(reservation));
            when(slotRepository.findBySlotIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(slot));
            when(paymentFeignClient.getPayment(reservationId)).thenReturn(apiResponse);
            givenTransactionExecutes();
            when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            givenLockAcquired();
            when(redissonClient.getAtomicLong(anyString())).thenReturn(atomicLong);

            reservationService.cancelReservation(reservationId, userId, null);

            verify(paymentFeignClient, never()).refund(any(), any());
        }

        @Test
        @DisplayName("다른 사용자의 예약 취소 시 RESERVATION_FORBIDDEN 예외가 발생한다")
        void fail_forbidden() {
            UUID reservationId = UUID.randomUUID();
            UUID reservationOwnerId = UUID.randomUUID();
            UUID requesterId = UUID.randomUUID();

            Reservation reservation = buildReservation(UUID.randomUUID(), reservationOwnerId, UUID.randomUUID());
            ReflectionTestUtils.setField(reservation, "reservationId", reservationId);

            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> reservationService.cancelReservation(reservationId, requesterId, null))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("visitReservation()")
    class VisitReservation {

        @Test
        @DisplayName("CONFIRMED 예약의 방문 처리 시 VISITED 상태가 된다")
        void success() {
            UUID storeId = UUID.randomUUID();
            UUID reservationId = UUID.randomUUID();
            Reservation reservation = buildReservation(UUID.randomUUID(), UUID.randomUUID(), storeId);
            ReflectionTestUtils.setField(reservation, "reservationId", reservationId);
            reservation.confirm();

            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(reservation));
            when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReservationResponse response = reservationService.visitReservation(storeId, reservationId);

            assertThat(response.getStatus()).isEqualTo(ReservationStatus.VISITED);
        }

        @Test
        @DisplayName("다른 매장의 예약 접근 시 RESERVATION_NOT_FOUND 예외가 발생한다")
        void fail_wrongStore() {
            UUID storeId = UUID.randomUUID();
            UUID differentStoreId = UUID.randomUUID();
            UUID reservationId = UUID.randomUUID();
            Reservation reservation = buildReservation(UUID.randomUUID(), UUID.randomUUID(), differentStoreId);
            ReflectionTestUtils.setField(reservation, "reservationId", reservationId);

            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> reservationService.visitReservation(storeId, reservationId))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_NOT_FOUND);
        }

        @Test
        @DisplayName("PAYMENT_PENDING 예약의 방문 처리 시 RESERVATION_NOT_VISITABLE 예외가 발생한다")
        void fail_notConfirmed() {
            UUID storeId = UUID.randomUUID();
            UUID reservationId = UUID.randomUUID();
            Reservation reservation = buildReservation(UUID.randomUUID(), UUID.randomUUID(), storeId);
            ReflectionTestUtils.setField(reservation, "reservationId", reservationId);

            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> reservationService.visitReservation(storeId, reservationId))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_NOT_VISITABLE);
        }
    }

    @Nested
    @DisplayName("noShowReservation()")
    class NoShowReservation {

        @Test
        @DisplayName("CONFIRMED 예약의 노쇼 처리 시 NO_SHOW 상태가 된다")
        void success() {
            UUID storeId = UUID.randomUUID();
            UUID reservationId = UUID.randomUUID();
            Reservation reservation = buildReservation(UUID.randomUUID(), UUID.randomUUID(), storeId);
            ReflectionTestUtils.setField(reservation, "reservationId", reservationId);
            reservation.confirm();

            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(reservation));
            when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReservationResponse response = reservationService.noShowReservation(storeId, reservationId);

            assertThat(response.getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
        }
    }

    @Nested
    @DisplayName("getMyReservation()")
    class GetMyReservation {

        @Test
        @DisplayName("본인의 예약을 조회하면 예약 정보가 반환된다")
        void success() {
            UUID userId = UUID.randomUUID();
            UUID reservationId = UUID.randomUUID();
            Reservation reservation = buildReservation(UUID.randomUUID(), userId, UUID.randomUUID());
            ReflectionTestUtils.setField(reservation, "reservationId", reservationId);

            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(reservation));

            ReservationResponse response = reservationService.getMyReservation(reservationId, userId);

            assertThat(response.getReservationId()).isEqualTo(reservationId);
        }

        @Test
        @DisplayName("다른 사용자의 예약 조회 시 RESERVATION_FORBIDDEN 예외가 발생한다")
        void fail_forbidden() {
            UUID userId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            UUID reservationId = UUID.randomUUID();
            Reservation reservation = buildReservation(UUID.randomUUID(), otherUserId, UUID.randomUUID());
            ReflectionTestUtils.setField(reservation, "reservationId", reservationId);

            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> reservationService.getMyReservation(reservationId, userId))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_FORBIDDEN);
        }

        @Test
        @DisplayName("존재하지 않는 예약 조회 시 RESERVATION_NOT_FOUND 예외가 발생한다")
        void fail_notFound() {
            UUID reservationId = UUID.randomUUID();
            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> reservationService.getMyReservation(reservationId, UUID.randomUUID()))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getMyReservations()")
    class GetMyReservations {

        @Test
        @DisplayName("사용자 ID로 예약 목록을 페이지로 조회한다")
        void success() {
            UUID userId = UUID.randomUUID();
            Reservation r1 = buildReservation(UUID.randomUUID(), userId, UUID.randomUUID());
            Reservation r2 = buildReservation(UUID.randomUUID(), userId, UUID.randomUUID());
            Pageable pageable = PageRequest.of(0, 10);

            when(reservationRepository.findMyReservations(eq(userId), isNull(), isNull(), isNull(), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(r1, r2), pageable, 2));

            Page<ReservationResponse> result = reservationService.getMyReservations(userId, null, null, null, pageable);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("from/to 파라미터를 자정 기준 LocalDateTime 범위로 변환해서 조회한다")
        void success_convertsDateRangeToDateTime() {
            UUID userId = UUID.randomUUID();
            LocalDate from = LocalDate.now();
            LocalDate to = LocalDate.now().plusDays(3);
            Pageable pageable = PageRequest.of(0, 10);

            when(reservationRepository.findMyReservations(any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            reservationService.getMyReservations(userId, ReservationStatus.CONFIRMED, from, to, pageable);

            verify(reservationRepository).findMyReservations(
                    userId, ReservationStatus.CONFIRMED,
                    from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                    pageable);
        }
    }

    @Nested
    @DisplayName("getStoreReservations()")
    class GetStoreReservations {

        @Test
        @DisplayName("date 파라미터를 하루 범위 LocalDateTime으로 변환해서 조회한다")
        void success_convertsDateToDayRange() {
            UUID storeId = UUID.randomUUID();
            LocalDate date = LocalDate.now().plusDays(1);
            Pageable pageable = PageRequest.of(0, 10);

            when(reservationRepository.findStoreReservations(any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            reservationService.getStoreReservations(storeId, null, date, pageable);

            verify(reservationRepository).findStoreReservations(
                    storeId, null,
                    date.atStartOfDay(), date.plusDays(1).atStartOfDay(),
                    pageable);
        }
    }

    @Nested
    @DisplayName("changeReservation()")
    class ChangeReservation {

        @Test
        @DisplayName("newSlotId 없이 인원수만 변경하면 기존 슬롯에서 증감 처리된다")
        void success_sizeOnly() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID storeId = UUID.randomUUID();
            UUID reservationId = UUID.randomUUID();
            UUID slotId = UUID.randomUUID();

            Reservation reservation = buildConfirmedReservation(
                    slotId, userId, storeId, LocalDateTime.now().plusDays(5), 2);
            ReflectionTestUtils.setField(reservation, "reservationId", reservationId);

            ReservationSlot slot = ReservationSlot.builder()
                    .storeId(storeId)
                    .slotDate(LocalDate.now().plusDays(5))
                    .slotTime(LocalTime.of(18, 0))
                    .maxCapacity(4)
                    .depositRequired(false)
                    .depositAmount(null)
                    .build();
            ReflectionTestUtils.setField(slot, "slotId", slotId);

            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(reservation));
            when(slotRepository.findBySlotIdAndDeletedAtIsNull(slotId)).thenReturn(Optional.of(slot));
            givenLockAcquired();
            when(redissonClient.getAtomicLong(anyString())).thenReturn(atomicLong);
            when(atomicLong.addAndGet(-1L)).thenReturn(1L);
            givenTransactionExecutes();
            when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReservationResponse response = reservationService.changeReservation(
                    reservationId, userId, buildChangeRequest(3, null));

            assertThat(response.getReservationSize()).isEqualTo(3);
            assertThat(response.getSlotId()).isEqualTo(slotId);
        }

        @Test
        @DisplayName("newSlotId가 다른 슬롯이면 이중 락을 잡고 두 슬롯 간 인원을 이동한다")
        void success_slotChange() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID storeId = UUID.randomUUID();
            UUID reservationId = UUID.randomUUID();

            ReservationSlot oldSlot = buildSlot(storeId, LocalDate.now().plusDays(5), LocalTime.of(18, 0), 4);
            ReservationSlot newSlot = buildSlot(storeId, LocalDate.now().plusDays(6), LocalTime.of(19, 0), 4);
            UUID oldSlotId = oldSlot.getSlotId();
            UUID newSlotId = newSlot.getSlotId();

            Reservation reservation = buildConfirmedReservation(
                    oldSlotId, userId, storeId, LocalDateTime.of(oldSlot.getSlotDate(), oldSlot.getSlotTime()), 2);
            ReflectionTestUtils.setField(reservation, "reservationId", reservationId);

            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(reservation));
            when(slotRepository.findBySlotIdAndDeletedAtIsNull(oldSlotId)).thenReturn(Optional.of(oldSlot));
            when(slotRepository.findBySlotIdAndDeletedAtIsNull(newSlotId)).thenReturn(Optional.of(newSlot));

            lockFor("reservation:lock:slot:" + oldSlotId);
            lockFor("reservation:lock:slot:" + newSlotId);
            atomicLongFor("slot:capacity:" + oldSlotId);
            RAtomicLong newCapacity = atomicLongFor("slot:capacity:" + newSlotId);
            when(newCapacity.addAndGet(-2L)).thenReturn(2L);

            givenTransactionExecutes();
            when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReservationResponse response = reservationService.changeReservation(
                    reservationId, userId, buildChangeRequest(null, newSlotId));

            assertThat(response.getSlotId()).isEqualTo(newSlotId);
            assertThat(response.getReservationSize()).isEqualTo(2);
        }

        @Test
        @DisplayName("락 획득 전 조회한 슬롯 정보가 그 사이 다른 요청으로 stale해지면 SLOT_LOCK_FAILED 예외가 발생한다")
        void fail_staleSlotSnapshotAfterLockAcquired() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID storeId = UUID.randomUUID();
            UUID reservationId = UUID.randomUUID();

            ReservationSlot oldSlot = buildSlot(storeId, LocalDate.now().plusDays(5), LocalTime.of(18, 0), 4);
            ReservationSlot newSlot = buildSlot(storeId, LocalDate.now().plusDays(6), LocalTime.of(19, 0), 4);
            UUID oldSlotId = oldSlot.getSlotId();
            UUID newSlotId = newSlot.getSlotId();
            UUID anotherSlotId = UUID.randomUUID(); // 락 획득 사이에 이미 다른 요청으로 이동해버린 슬롯

            Reservation reservationAtOldSlot = buildConfirmedReservation(
                    oldSlotId, userId, storeId, LocalDateTime.of(oldSlot.getSlotDate(), oldSlot.getSlotTime()), 2);
            ReflectionTestUtils.setField(reservationAtOldSlot, "reservationId", reservationId);

            Reservation reservationMovedAway = buildConfirmedReservation(
                    anotherSlotId, userId, storeId, LocalDateTime.now().plusDays(7), 2);
            ReflectionTestUtils.setField(reservationMovedAway, "reservationId", reservationId);

            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(reservationAtOldSlot))   // changeReservation()의 최초 조회 (oldSlotId 스냅샷)
                    .thenReturn(Optional.of(reservationAtOldSlot))   // changeReservationSlot()의 매장 일치 사전 검증
                    .thenReturn(Optional.of(reservationMovedAway));  // 락 획득 후 재조회 시점엔 이미 다른 슬롯으로 이동한 상태
            when(slotRepository.findBySlotIdAndDeletedAtIsNull(newSlotId)).thenReturn(Optional.of(newSlot));

            lockFor("reservation:lock:slot:" + oldSlotId);
            lockFor("reservation:lock:slot:" + newSlotId);

            assertThatThrownBy(() -> reservationService.changeReservation(
                    reservationId, userId, buildChangeRequest(null, newSlotId)))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.SLOT_LOCK_FAILED);
        }

        @Test
        @DisplayName("타 매장 슬롯으로 변경 시도 시 RESERVATION_SLOT_STORE_MISMATCH 예외가 발생한다")
        void fail_storeMismatch() {
            UUID userId = UUID.randomUUID();
            UUID storeId = UUID.randomUUID();
            UUID otherStoreId = UUID.randomUUID();
            UUID reservationId = UUID.randomUUID();
            UUID oldSlotId = UUID.randomUUID();

            Reservation reservation = buildConfirmedReservation(
                    oldSlotId, userId, storeId, LocalDateTime.now().plusDays(5), 2);
            ReflectionTestUtils.setField(reservation, "reservationId", reservationId);

            ReservationSlot newSlot = buildSlot(otherStoreId, LocalDate.now().plusDays(6), LocalTime.of(19, 0), 4);
            UUID newSlotId = newSlot.getSlotId();

            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(reservation));
            when(slotRepository.findBySlotIdAndDeletedAtIsNull(newSlotId)).thenReturn(Optional.of(newSlot));

            assertThatThrownBy(() -> reservationService.changeReservation(
                    reservationId, userId, buildChangeRequest(null, newSlotId)))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_SLOT_STORE_MISMATCH);
        }

        @Test
        @DisplayName("신규 슬롯 인원 초과 시 RESERVATION_SLOT_CAPACITY_EXCEEDED 예외가 발생하고 양쪽 Redis가 롤백된다")
        void fail_newSlotCapacityExceeded() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID storeId = UUID.randomUUID();
            UUID reservationId = UUID.randomUUID();

            ReservationSlot oldSlot = buildSlot(storeId, LocalDate.now().plusDays(5), LocalTime.of(18, 0), 4);
            ReservationSlot newSlot = buildSlot(storeId, LocalDate.now().plusDays(6), LocalTime.of(19, 0), 1);
            UUID oldSlotId = oldSlot.getSlotId();
            UUID newSlotId = newSlot.getSlotId();

            Reservation reservation = buildConfirmedReservation(
                    oldSlotId, userId, storeId, LocalDateTime.of(oldSlot.getSlotDate(), oldSlot.getSlotTime()), 2);
            ReflectionTestUtils.setField(reservation, "reservationId", reservationId);

            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(reservation));
            when(slotRepository.findBySlotIdAndDeletedAtIsNull(newSlotId)).thenReturn(Optional.of(newSlot));

            lockFor("reservation:lock:slot:" + oldSlotId);
            lockFor("reservation:lock:slot:" + newSlotId);
            RAtomicLong oldCapacity = atomicLongFor("slot:capacity:" + oldSlotId);
            RAtomicLong newCapacity = atomicLongFor("slot:capacity:" + newSlotId);
            when(newCapacity.addAndGet(-2L)).thenReturn(-1L);

            assertThatThrownBy(() -> reservationService.changeReservation(
                    reservationId, userId, buildChangeRequest(null, newSlotId)))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_SLOT_CAPACITY_EXCEEDED);

            verify(newCapacity).addAndGet(2L);   // 롤백: 신규 슬롯 감소분 원복
            verify(oldCapacity).addAndGet(-2L);  // 롤백: 기존 슬롯 증가분 원복
        }

        @Test
        @DisplayName("새 슬롯 날짜가 오늘 이전이면 RESERVATION_SLOT_NOT_CHANGEABLE 예외가 발생한다")
        void fail_newSlotNotAfterToday() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID storeId = UUID.randomUUID();
            UUID reservationId = UUID.randomUUID();

            ReservationSlot oldSlot = buildSlot(storeId, LocalDate.now().plusDays(5), LocalTime.of(18, 0), 4);
            ReservationSlot newSlot = buildSlot(storeId, LocalDate.now(), LocalTime.of(19, 0), 4);
            UUID oldSlotId = oldSlot.getSlotId();
            UUID newSlotId = newSlot.getSlotId();

            Reservation reservation = buildConfirmedReservation(
                    oldSlotId, userId, storeId, LocalDateTime.of(oldSlot.getSlotDate(), oldSlot.getSlotTime()), 2);
            ReflectionTestUtils.setField(reservation, "reservationId", reservationId);

            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(reservation));
            when(slotRepository.findBySlotIdAndDeletedAtIsNull(newSlotId)).thenReturn(Optional.of(newSlot));

            lockFor("reservation:lock:slot:" + oldSlotId);
            lockFor("reservation:lock:slot:" + newSlotId);
            RAtomicLong oldCapacity = atomicLongFor("slot:capacity:" + oldSlotId);
            RAtomicLong newCapacity = atomicLongFor("slot:capacity:" + newSlotId);
            when(newCapacity.addAndGet(-2L)).thenReturn(2L);
            givenTransactionExecutes();

            assertThatThrownBy(() -> reservationService.changeReservation(
                    reservationId, userId, buildChangeRequest(null, newSlotId)))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_SLOT_NOT_CHANGEABLE);

            verify(newCapacity).addAndGet(2L);   // 롤백: 신규 슬롯 감소분 원복
            verify(oldCapacity).addAndGet(-2L);  // 롤백: 기존 슬롯 증가분 원복
        }

        @Test
        @DisplayName("다른 사용자의 예약을 변경하려 하면 RESERVATION_FORBIDDEN 예외가 발생한다")
        void fail_forbidden() {
            UUID reservationOwnerId = UUID.randomUUID();
            UUID requesterId = UUID.randomUUID();
            UUID reservationId = UUID.randomUUID();

            Reservation reservation = buildReservation(UUID.randomUUID(), reservationOwnerId, UUID.randomUUID());
            ReflectionTestUtils.setField(reservation, "reservationId", reservationId);

            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> reservationService.changeReservation(
                    reservationId, requesterId, buildChangeRequest(3, null)))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_FORBIDDEN);
        }
    }
}
