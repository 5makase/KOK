package com.omakase.kok.reservation.domain.repository;

import com.omakase.kok.reservation.domain.entity.ReservationIdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReservationIdempotencyKeyRepository extends JpaRepository<ReservationIdempotencyKey, UUID> {

    Optional<ReservationIdempotencyKey> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
