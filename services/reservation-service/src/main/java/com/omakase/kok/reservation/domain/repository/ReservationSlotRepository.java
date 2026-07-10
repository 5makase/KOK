package com.omakase.kok.reservation.domain.repository;

import com.omakase.kok.reservation.domain.entity.ReservationSlot;
import com.omakase.kok.reservation.domain.enums.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationSlotRepository extends JpaRepository<ReservationSlot, UUID> {

    Optional<ReservationSlot> findBySlotIdAndDeletedAtIsNull(UUID slotId);

    List<ReservationSlot> findByStoreIdAndDeletedAtIsNull(UUID storeId);

    List<ReservationSlot> findByStoreIdAndSlotDateAndDeletedAtIsNull(UUID storeId, LocalDate slotDate);

    List<ReservationSlot> findByStoreIdAndStatusAndSlotDateGreaterThanEqualAndDeletedAtIsNull(
            UUID storeId, SlotStatus status, LocalDate from);

    List<ReservationSlot> findByStoreIdAndStatusAndSlotDateAndDeletedAtIsNull(
            UUID storeId, SlotStatus status, LocalDate slotDate);

    List<ReservationSlot> findByStatusAndSlotDateGreaterThanEqualAndDeletedAtIsNull(
            SlotStatus status, LocalDate from);
}
