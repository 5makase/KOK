package com.omakase.kok.store.domain.entity;

import com.omakase.kok.store.domain.enums.OutboxEventStatus;
import com.omakase.kok.store.domain.enums.StoreEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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

@Entity
@Table(
    name = "p_store_outbox_events",
    indexes = @Index(name = "idx_store_outbox_status_created", columnList = "status, created_at")
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreOutboxEvent {

    private static final int MAX_RETRY_COUNT = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "store_outbox_event_id", nullable = false, updatable = false)
    private UUID storeOutboxEventId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private StoreEventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "failed_reason", length = 200)
    private String failedReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    private StoreOutboxEvent(UUID storeId, StoreEventType eventType, String payload) {
        this.storeId = storeId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
        this.retryCount = 0;
    }

    public void published() {
        if (this.status != OutboxEventStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 발행 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.failedReason = null;
    }

    public void failed(String reason) {
        if (this.status != OutboxEventStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 실패 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        this.retryCount++;
        this.failedReason = reason;
        if (this.retryCount >= MAX_RETRY_COUNT) {
            this.status = OutboxEventStatus.FAILED;
        }
    }

    public void retry() {
        if (this.status != OutboxEventStatus.FAILED) {
            throw new IllegalStateException("FAILED 상태에서만 재시도할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = OutboxEventStatus.PENDING;
        this.failedReason = null;
    }
}
