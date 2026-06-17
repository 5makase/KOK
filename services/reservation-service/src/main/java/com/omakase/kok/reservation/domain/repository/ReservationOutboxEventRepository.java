package com.omakase.kok.reservation.domain.repository;

import com.omakase.kok.reservation.domain.entity.ReservationOutboxEvent;
import com.omakase.kok.reservation.domain.enums.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReservationOutboxEventRepository extends JpaRepository<ReservationOutboxEvent, UUID> {

    List<ReservationOutboxEvent> findByStatus(OutboxEventStatus status);
}
