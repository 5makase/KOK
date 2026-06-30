package com.omakase.kok.reservation.application.service;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.reservation.application.dto.CreateSlotRequest;
import com.omakase.kok.reservation.application.dto.SlotResponse;
import com.omakase.kok.reservation.domain.entity.Reservation;
import com.omakase.kok.reservation.domain.entity.ReservationSlot;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import com.omakase.kok.reservation.domain.exception.SlotErrorCode;
import com.omakase.kok.reservation.domain.repository.ReservationRepository;
import com.omakase.kok.reservation.domain.repository.ReservationSlotRepository;
import com.omakase.kok.reservation.infrastructure.client.StoreServiceFeignClient;
import com.omakase.kok.reservation.infrastructure.client.dto.BusinessHoursValidationResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SlotService 단위 테스트")
class SlotServiceTest {

    @Mock private ReservationSlotRepository slotRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private RedissonClient redissonClient;
    @Mock private RAtomicLong atomicLong;
    @Mock private StoreServiceFeignClient storeServiceFeignClient;

    private static final LocalDate SLOT_DATE = LocalDate.of(2026, 7, 1);

    private SlotService slotService;

    @BeforeEach
    void setUp() {
        slotService = new SlotService(slotRepository, reservationRepository, redissonClient, storeServiceFeignClient);
    }

    private void stubValidationOk() {
        BusinessHoursValidationResponse available = new BusinessHoursValidationResponse();
        org.springframework.test.util.ReflectionTestUtils.setField(available, "available", true);
        when(storeServiceFeignClient.validateBusinessHours(any(), any(), any()))
                .thenReturn(ApiResponse.success(available));
    }

    private CreateSlotRequest buildCreateRequest(UUID storeId, boolean depositRequired, Long depositAmount) {
        CreateSlotRequest req = new CreateSlotRequest();
        ReflectionTestUtils.setField(req, "storeId", storeId);
        ReflectionTestUtils.setField(req, "slotDate", SLOT_DATE);
        ReflectionTestUtils.setField(req, "slotTime", LocalTime.of(18, 0));
        ReflectionTestUtils.setField(req, "maxCapacity", 4);
        ReflectionTestUtils.setField(req, "depositRequired", depositRequired);
        ReflectionTestUtils.setField(req, "depositAmount", depositAmount);
        return req;
    }

    private ReservationSlot buildSlot(UUID storeId) {
        ReservationSlot slot = ReservationSlot.builder()
                .storeId(storeId)
                .slotDate(SLOT_DATE)
                .slotTime(LocalTime.of(18, 0))
                .maxCapacity(4)
                .depositRequired(false)
                .depositAmount(null)
                .build();
        ReflectionTestUtils.setField(slot, "slotId", UUID.randomUUID());
        return slot;
    }

    @Nested
    @DisplayName("CreateSlotRequest 유효성 검사")
    class CreateSlotRequestValidation {

        private Validator validator;

        @BeforeEach
        void setUpValidator() {
            validator = Validation.buildDefaultValidatorFactory().getValidator();
        }

        @Test
        @DisplayName("예약금 필요 슬롯에 depositAmount=0 설정 시 유효성 위반이 발생한다")
        void fail_depositAmountZero() {
            CreateSlotRequest req = new CreateSlotRequest();
            ReflectionTestUtils.setField(req, "storeId", UUID.randomUUID());
            ReflectionTestUtils.setField(req, "storeName", "테스트 매장");
            ReflectionTestUtils.setField(req, "slotDate", SLOT_DATE);
            ReflectionTestUtils.setField(req, "slotTime", LocalTime.of(18, 0));
            ReflectionTestUtils.setField(req, "maxCapacity", 4);
            ReflectionTestUtils.setField(req, "depositRequired", true);
            ReflectionTestUtils.setField(req, "depositAmount", 0L);

            Set<ConstraintViolation<CreateSlotRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("depositAmountValid"));
        }

        @Test
        @DisplayName("예약금 필요 슬롯에 depositAmount=null 설정 시 유효성 위반이 발생한다")
        void fail_depositAmountNull() {
            CreateSlotRequest req = new CreateSlotRequest();
            ReflectionTestUtils.setField(req, "storeId", UUID.randomUUID());
            ReflectionTestUtils.setField(req, "storeName", "테스트 매장");
            ReflectionTestUtils.setField(req, "slotDate", SLOT_DATE);
            ReflectionTestUtils.setField(req, "slotTime", LocalTime.of(18, 0));
            ReflectionTestUtils.setField(req, "maxCapacity", 4);
            ReflectionTestUtils.setField(req, "depositRequired", true);

            Set<ConstraintViolation<CreateSlotRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("depositAmountValid"));
        }
    }

    @Nested
    @DisplayName("createSlot()")
    class CreateSlot {

        @Test
        @DisplayName("슬롯 저장 후 afterCommit에서 Redis에 잔여 인원이 설정된다")
        void success_redisSetOnAfterCommit() {
            UUID storeId = UUID.randomUUID();
            CreateSlotRequest req = buildCreateRequest(storeId, false, null);
            stubValidationOk();

            ArgumentCaptor<String> redisKeyCaptor = ArgumentCaptor.forClass(String.class);
            UUID[] savedSlotId = new UUID[1];

            when(slotRepository.save(any(ReservationSlot.class))).thenAnswer(inv -> {
                ReservationSlot s = inv.getArgument(0);
                savedSlotId[0] = UUID.randomUUID();
                ReflectionTestUtils.setField(s, "slotId", savedSlotId[0]);
                return s;
            });
            when(redissonClient.getAtomicLong(redisKeyCaptor.capture())).thenReturn(atomicLong);

            try (MockedStatic<TransactionSynchronizationManager> mocked =
                         mockStatic(TransactionSynchronizationManager.class)) {
                mocked.when(() -> TransactionSynchronizationManager.registerSynchronization(any()))
                        .thenAnswer(inv -> {
                            TransactionSynchronization sync = inv.getArgument(0);
                            sync.afterCommit();
                            return null;
                        });

                SlotResponse response = slotService.createSlot(req, storeId);

                assertThat(response).isNotNull();
                verify(atomicLong).set(4);
                assertThat(redisKeyCaptor.getValue()).contains(savedSlotId[0].toString());
            }
        }

        @Test
        @DisplayName("예약금 필요 슬롯 생성 시 depositAmount가 포함된 슬롯이 저장된다")
        void success_withDeposit() {
            UUID storeId = UUID.randomUUID();
            CreateSlotRequest req = buildCreateRequest(storeId, true, 10000L);
            stubValidationOk();

            when(slotRepository.save(any(ReservationSlot.class))).thenAnswer(inv -> {
                ReservationSlot s = inv.getArgument(0);
                ReflectionTestUtils.setField(s, "slotId", UUID.randomUUID());
                return s;
            });
            when(redissonClient.getAtomicLong(anyString())).thenReturn(atomicLong);

            try (MockedStatic<TransactionSynchronizationManager> mocked =
                         mockStatic(TransactionSynchronizationManager.class)) {
                mocked.when(() -> TransactionSynchronizationManager.registerSynchronization(any()))
                        .thenAnswer(inv -> {
                            TransactionSynchronization sync = inv.getArgument(0);
                            sync.afterCommit();
                            return null;
                        });

                SlotResponse response = slotService.createSlot(req, storeId);

                assertThat(response.isDepositRequired()).isTrue();
                assertThat(response.getDepositAmount()).isEqualTo(10000L);
            }
        }
    }

    @Nested
    @DisplayName("getSlots()")
    class GetSlots {

        @Test
        @DisplayName("날짜 없이 storeId로 조회 시 해당 매장의 모든 슬롯을 반환한다")
        void success_withoutDate() {
            UUID storeId = UUID.randomUUID();
            ReservationSlot s1 = buildSlot(storeId);
            ReservationSlot s2 = buildSlot(storeId);

            when(slotRepository.findByStoreIdAndDeletedAtIsNull(storeId))
                    .thenReturn(List.of(s1, s2));

            List<SlotResponse> result = slotService.getSlots(storeId, null);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("날짜 포함 조회 시 해당 날짜의 슬롯만 반환한다")
        void success_withDate() {
            UUID storeId = UUID.randomUUID();
            ReservationSlot s1 = buildSlot(storeId);

            when(slotRepository.findByStoreIdAndSlotDateAndDeletedAtIsNull(storeId, SLOT_DATE))
                    .thenReturn(List.of(s1));

            List<SlotResponse> result = slotService.getSlots(storeId, SLOT_DATE);

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("deleteSlot()")
    class DeleteSlot {

        @Test
        @DisplayName("활성 예약이 없는 슬롯은 삭제되고 Redis 키가 제거된다")
        void success() {
            UUID slotId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            ReservationSlot slot = buildSlot(UUID.randomUUID());
            ReflectionTestUtils.setField(slot, "slotId", slotId);

            when(slotRepository.findBySlotIdAndDeletedAtIsNull(slotId))
                    .thenReturn(Optional.of(slot));
            when(reservationRepository.findBySlotIdAndStatusInAndDeletedAtIsNull(
                    eq(slotId), anyList()))
                    .thenReturn(List.of());
            when(redissonClient.getAtomicLong(anyString())).thenReturn(atomicLong);

            slotService.deleteSlot(slotId, ownerId);

            verify(atomicLong).delete();
        }

        @Test
        @DisplayName("활성 예약(PAYMENT_PENDING)이 있으면 SLOT_HAS_ACTIVE_RESERVATION 예외가 발생한다")
        void fail_hasActivePendingReservation() {
            UUID slotId = UUID.randomUUID();
            ReservationSlot slot = buildSlot(UUID.randomUUID());
            ReflectionTestUtils.setField(slot, "slotId", slotId);

            Reservation activeReservation = Reservation.builder()
                    .slotId(slotId)
                    .userId(UUID.randomUUID())
                    .storeId(UUID.randomUUID())
                    .bookerName("홍길동")
                    .bookerPhone("010-1234-5678")
                    .reservationSize(2)
                    .build();

            when(slotRepository.findBySlotIdAndDeletedAtIsNull(slotId))
                    .thenReturn(Optional.of(slot));
            when(reservationRepository.findBySlotIdAndStatusInAndDeletedAtIsNull(
                    eq(slotId), eq(List.of(ReservationStatus.PAYMENT_PENDING, ReservationStatus.CONFIRMED))))
                    .thenReturn(List.of(activeReservation));

            assertThatThrownBy(() -> slotService.deleteSlot(slotId, UUID.randomUUID()))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(SlotErrorCode.SLOT_HAS_ACTIVE_RESERVATION);

            verify(redissonClient, never()).getAtomicLong(anyString());
        }

        @Test
        @DisplayName("존재하지 않는 슬롯 삭제 시 SLOT_NOT_FOUND 예외가 발생한다")
        void fail_slotNotFound() {
            UUID slotId = UUID.randomUUID();
            when(slotRepository.findBySlotIdAndDeletedAtIsNull(slotId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> slotService.deleteSlot(slotId, UUID.randomUUID()))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(SlotErrorCode.SLOT_NOT_FOUND);
        }
    }
}
