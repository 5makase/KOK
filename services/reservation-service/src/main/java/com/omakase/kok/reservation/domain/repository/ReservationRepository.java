package com.omakase.kok.reservation.domain.repository;

import com.omakase.kok.reservation.domain.entity.Reservation;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByUserIdAndDeletedAtIsNull(UUID userId);

    List<Reservation> findBySlotIdAndStatusIn(UUID slotId, List<ReservationStatus> statuses);

    // PENDING 타임아웃 대상 조회
    List<Reservation> findByStatusAndCreatedAtBefore(ReservationStatus status, LocalDateTime threshold);
}
