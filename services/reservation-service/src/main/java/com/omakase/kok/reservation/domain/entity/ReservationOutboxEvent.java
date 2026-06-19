package com.omakase.kok.reservation.domain.entity;

import com.omakase.kok.reservation.domain.enums.EventType;
import com.omakase.kok.reservation.domain.enums.OutboxEventStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_reservation_outbox_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "reservation_outbox_event_id", updatable = false, nullable = false)
    private UUID outboxEventId;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "failed_reason", length = 500)
    private String failedReason;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public ReservationOutboxEvent(UUID reservationId, EventType eventType, String payload) {
        this.reservationId = reservationId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = LocalDateTime.now();
    }

    public void published() {
        if (this.status != OutboxEventStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 발행 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void failed(String reason) {
        if (this.status != OutboxEventStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 실패 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        this.retryCount++;
        if (this.retryCount >= 5) {
            this.status = OutboxEventStatus.FAILED;
        }
        this.failedReason = reason;
    }
}
