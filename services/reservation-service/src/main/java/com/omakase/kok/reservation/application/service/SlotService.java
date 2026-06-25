package com.omakase.kok.reservation.application.service;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.reservation.application.dto.CreateSlotRequest;
import com.omakase.kok.reservation.application.dto.SlotResponse;
import com.omakase.kok.reservation.application.dto.UpdateSlotRequest;
import com.omakase.kok.reservation.domain.entity.ReservationSlot;
import com.omakase.kok.reservation.domain.enums.SlotStatus;
import com.omakase.kok.reservation.domain.exception.SlotErrorCode;
import com.omakase.kok.reservation.domain.repository.ReservationRepository;
import com.omakase.kok.reservation.domain.repository.ReservationSlotRepository;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SlotService {

    private static final String SLOT_CAPACITY_KEY = "slot:capacity:";

    private final ReservationSlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final RedissonClient redissonClient;

    @Transactional
    public SlotResponse createSlot(CreateSlotRequest request, UUID ownerId) {
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

    @Transactional
    public SlotResponse updateSlot(UUID slotId, UpdateSlotRequest request, UUID ownerId) {
        ReservationSlot slot = slotRepository.findBySlotIdAndDeletedAtIsNull(slotId)
                .orElseThrow(() -> new BaseException(SlotErrorCode.SLOT_NOT_FOUND));

        Integer newMaxCapacity = request.getMaxCapacity();
        if (newMaxCapacity != null) {
            int used = slot.getMaxCapacity() - slot.getRemainingCapacity();
            if (newMaxCapacity < used) {
                throw new BaseException(SlotErrorCode.SLOT_CAPACITY_BELOW_USED);
            }
        }

        slot.update(request.getSlotDate(), request.getSlotTime(), newMaxCapacity,
                request.getDepositRequired(), request.getDepositAmount());

        if (newMaxCapacity != null) {
            int updatedRemaining = slot.getRemainingCapacity();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slotId).set(updatedRemaining);
                }
            });
        }

        return SlotResponse.from(slot);
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
