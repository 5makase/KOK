package com.omakase.kok.reservation.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_reservation_idempotency_keys",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "idempotency_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationIdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public ReservationIdempotencyKey(UUID userId, String idempotencyKey, UUID reservationId) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.reservationId = reservationId;
        this.createdAt = LocalDateTime.now();
    }
}
