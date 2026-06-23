package com.omakase.kok.waiting.domain.entity;

import com.omakase.kok.waiting.domain.enums.OutboxStatus;
import com.omakase.kok.waiting.domain.enums.WaitingEventType;
import com.omakase.kok.waiting.global.exception.WaitingErrorCode;
import com.omakase.kok.waiting.global.exception.WaitingException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.EntityListeners;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_waiting_outbox_events")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitingOutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "outbox_event_id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waiting_id", nullable = false)
    private Waiting waiting;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private WaitingEventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "failed_reason", columnDefinition = "text")
    private String failedReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    private WaitingOutboxEvent(Waiting waiting, WaitingEventType eventType, String payload) {
        this.waiting = waiting;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
    }

    public void publish() {
        if (this.status != OutboxStatus.PENDING) {
            throw new WaitingException(WaitingErrorCode.WAITING_OUTBOX_STATUS_NOT_ALLOWED);
        }
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.failedReason = null;
    }

    public void fail(String failedReason) {
        if (this.status != OutboxStatus.PENDING) {
            throw new WaitingException(WaitingErrorCode.WAITING_OUTBOX_STATUS_NOT_ALLOWED);
        }
        this.retryCount += 1;
        if (this.retryCount >= 2) {
            this.status = OutboxStatus.DEAD_LETTER;
        } else {
            this.status = OutboxStatus.FAILED;
        }
        this.failedReason = failedReason;
    }

    public void retry() {
        if (this.status != OutboxStatus.FAILED) {
            throw new WaitingException(WaitingErrorCode.WAITING_OUTBOX_STATUS_NOT_ALLOWED);
        }
        this.status = OutboxStatus.PENDING;
        this.failedReason = null;
    }
}
