package com.omakase.kok.reservation.domain.repository;

import com.omakase.kok.reservation.domain.entity.Reservation;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByUserIdAndDeletedAtIsNull(UUID userId);

    List<Reservation> findBySlotIdAndStatusInAndDeletedAtIsNull(UUID slotId, List<ReservationStatus> statuses);

    List<Reservation> findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(ReservationStatus status, LocalDateTime threshold);
}
