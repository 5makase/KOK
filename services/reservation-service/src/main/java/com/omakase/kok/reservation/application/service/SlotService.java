package com.omakase.kok.reservation.application.service;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.reservation.application.dto.CreateSlotRequest;
import com.omakase.kok.reservation.application.dto.SlotResponse;
import com.omakase.kok.reservation.application.dto.UpdateSlotRequest;
import com.omakase.kok.reservation.domain.entity.ReservationSlot;
import com.omakase.kok.reservation.domain.enums.SlotStatus;
import com.omakase.kok.reservation.domain.exception.ReservationErrorCode;
import com.omakase.kok.reservation.domain.exception.SlotErrorCode;
import com.omakase.kok.reservation.domain.repository.ReservationRepository;
import com.omakase.kok.reservation.domain.repository.ReservationSlotRepository;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import com.omakase.kok.reservation.infrastructure.client.StoreServiceFeignClient;
import com.omakase.kok.reservation.infrastructure.client.dto.BusinessHoursValidationResponse;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SlotService {

    private static final String SLOT_CAPACITY_KEY = "slot:capacity:";
    private static final String SLOT_LOCK_KEY = "reservation:lock:slot:";

    private final ReservationSlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final RedissonClient redissonClient;
    private final StoreServiceFeignClient storeServiceFeignClient;
    private final PlatformTransactionManager transactionManager;

    @Transactional
    public SlotResponse createSlot(CreateSlotRequest request, UUID ownerId) {
        validateBusinessHours(request);

        ReservationSlot slot = ReservationSlot.builder()
                .storeId(request.getStoreId())
                .storeName(request.getStoreName())
                .slotDate(request.getSlotDate())
                .slotTime(request.getSlotTime())
                .maxCapacity(request.getMaxCapacity())
                .depositRequired(request.isDepositRequired())
                .depositAmount(request.getDepositAmount())
                .build();

        slotRepository.save(slot);

        UUID slotId = slot.getSlotId();
        int maxCapacity = request.getMaxCapacity();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slotId).set(maxCapacity);
            }
        });

        return SlotResponse.from(slot);
    }

    public SlotResponse getSlot(UUID slotId) {
        ReservationSlot slot = slotRepository.findBySlotIdAndDeletedAtIsNull(slotId)
                .orElseThrow(() -> new BaseException(SlotErrorCode.SLOT_NOT_FOUND));
        return SlotResponse.from(slot);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SlotResponse updateSlot(UUID slotId, UpdateSlotRequest request, UUID ownerId) {
        RLock lock = redissonClient.getLock(SLOT_LOCK_KEY + slotId);
        try {
            if (!lock.tryLock(3, TimeUnit.SECONDS)) {
                throw new BaseException(ReservationErrorCode.SLOT_LOCK_FAILED);
            }

            Integer newMaxCapacity = request.getMaxCapacity();
            ReservationSlot slot = new TransactionTemplate(transactionManager).execute(status -> {
                ReservationSlot s = slotRepository.findBySlotIdAndDeletedAtIsNull(slotId)
                        .orElseThrow(() -> new BaseException(SlotErrorCode.SLOT_NOT_FOUND));

                if (newMaxCapacity != null) {
                    int used = s.getMaxCapacity() - s.getRemainingCapacity();
                    if (newMaxCapacity < used) {
                        throw new BaseException(SlotErrorCode.SLOT_CAPACITY_BELOW_USED);
                    }
                }

                s.update(request.getSlotDate(), request.getSlotTime(), newMaxCapacity,
                        request.getDepositRequired(), request.getDepositAmount());
                return s;
            });

            if (newMaxCapacity != null) {
                redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slotId).set(slot.getRemainingCapacity());
            }

            return SlotResponse.from(slot);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException(ReservationErrorCode.SLOT_LOCK_FAILED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public List<SlotResponse> getAvailableSlots(UUID storeId, LocalDate date) {
        List<ReservationSlot> slots = (date != null)
                ? slotRepository.findByStoreIdAndStatusAndSlotDateAndDeletedAtIsNull(storeId, SlotStatus.OPEN, date)
                : slotRepository.findByStoreIdAndStatusAndSlotDateGreaterThanEqualAndDeletedAtIsNull(
                        storeId, SlotStatus.OPEN, LocalDate.now());

        return slots.stream()
                .map(SlotResponse::from)
                .toList();
    }

    public List<SlotResponse> getSlots(UUID storeId, LocalDate date) {
        List<ReservationSlot> slots = (date != null)
                ? slotRepository.findByStoreIdAndSlotDateAndDeletedAtIsNull(storeId, date)
                : slotRepository.findByStoreIdAndDeletedAtIsNull(storeId);

        return slots.stream()
                .map(SlotResponse::from)
                .toList();
    }

    private void validateBusinessHours(CreateSlotRequest request) {
        BusinessHoursValidationResponse validation = storeServiceFeignClient
                .validateBusinessHours(request.getStoreId(), request.getSlotDate(), request.getSlotTime())
                .getData();

        if (!validation.isAvailable()) {
            SlotErrorCode errorCode = switch (validation.getReason()) {
                case "STORE_NOT_OPEN" -> SlotErrorCode.SLOT_STORE_NOT_OPEN;
                case "DAY_OFF"        -> SlotErrorCode.SLOT_ON_DAY_OFF;
                case "OUTSIDE_HOURS"  -> SlotErrorCode.SLOT_OUTSIDE_BUSINESS_HOURS;
                case "BREAK_TIME"     -> SlotErrorCode.SLOT_IN_BREAK_TIME;
                default               -> throw new IllegalStateException("Unknown validation reason: " + validation.getReason());
            };
            throw new BaseException(errorCode);
        }
    }

    @Transactional
    public void deleteSlot(UUID slotId, UUID ownerId) {
        ReservationSlot slot = slotRepository.findBySlotIdAndDeletedAtIsNull(slotId)
                .orElseThrow(() -> new BaseException(SlotErrorCode.SLOT_NOT_FOUND));

        boolean hasActiveReservation = !reservationRepository
                .findBySlotIdAndStatusInAndDeletedAtIsNull(
                        slotId,
                        List.of(ReservationStatus.PAYMENT_PENDING, ReservationStatus.CONFIRMED)
                )
                .isEmpty();

        if (hasActiveReservation) {
            throw new BaseException(SlotErrorCode.SLOT_HAS_ACTIVE_RESERVATION);
        }

        slot.delete(ownerId);

        redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slotId).delete();
    }
}
