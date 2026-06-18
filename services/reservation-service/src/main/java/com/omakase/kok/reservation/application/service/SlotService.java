package com.omakase.kok.reservation.application.service;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.reservation.application.dto.CreateSlotRequest;
import com.omakase.kok.reservation.application.dto.SlotResponse;
import com.omakase.kok.reservation.domain.entity.ReservationSlot;
import com.omakase.kok.reservation.domain.exception.SlotErrorCode;
import com.omakase.kok.reservation.domain.repository.ReservationRepository;
import com.omakase.kok.reservation.domain.repository.ReservationSlotRepository;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .slotDate(request.getSlotDate())
                .slotTime(request.getSlotTime())
                .maxCapacity(request.getMaxCapacity())
                .depositRequired(request.isDepositRequired())
                .depositAmount(request.getDepositAmount())
                .build();

        slotRepository.save(slot);

        redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slot.getSlotId())
                .set(request.getMaxCapacity());

        return SlotResponse.from(slot);
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

        slot.delete(ownerId.toString());

        redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slotId).delete();
    }
}
