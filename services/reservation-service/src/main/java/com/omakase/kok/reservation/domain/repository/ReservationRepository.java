package com.omakase.kok.reservation.domain.repository;

import com.omakase.kok.reservation.domain.entity.Reservation;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByReservationIdAndDeletedAtIsNull(UUID reservationId);

    List<Reservation> findByUserIdAndDeletedAtIsNull(UUID userId);

    List<Reservation> findByStoreIdAndDeletedAtIsNull(UUID storeId);

    List<Reservation> findBySlotIdAndStatusInAndDeletedAtIsNull(UUID slotId, List<ReservationStatus> statuses);

    List<Reservation> findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(ReservationStatus status, LocalDateTime threshold);

    List<Reservation> findByStatusAndScheduledAtBetweenAndDeletedAtIsNull(
            ReservationStatus status, LocalDateTime from, LocalDateTime to);

    @Query("""
        SELECT r FROM Reservation r
        WHERE r.userId = :userId AND r.deletedAt IS NULL
          AND (:status IS NULL OR r.status = :status)
          AND (:from IS NULL OR r.scheduledAt >= :from)
          AND (:to IS NULL OR r.scheduledAt < :to)
        """)
    Page<Reservation> findMyReservations(@Param("userId") UUID userId,
                                          @Param("status") ReservationStatus status,
                                          @Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to,
                                          Pageable pageable);

    @Query("""
        SELECT r FROM Reservation r
        WHERE r.storeId = :storeId AND r.deletedAt IS NULL
          AND (:status IS NULL OR r.status = :status)
          AND (:dateStart IS NULL OR (r.scheduledAt >= :dateStart AND r.scheduledAt < :dateEnd))
        """)
    Page<Reservation> findStoreReservations(@Param("storeId") UUID storeId,
                                             @Param("status") ReservationStatus status,
                                             @Param("dateStart") LocalDateTime dateStart,
                                             @Param("dateEnd") LocalDateTime dateEnd,
                                             Pageable pageable);
}
