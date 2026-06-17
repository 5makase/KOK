package com.omakase.kok.reservation.domain.repository;

import com.omakase.kok.reservation.domain.entity.ReservationSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReservationSlotRepository extends JpaRepository<ReservationSlot, UUID> {

    Optional<ReservationSlot> findBySlotIdAndDeletedAtIsNull(UUID slotId);
}
