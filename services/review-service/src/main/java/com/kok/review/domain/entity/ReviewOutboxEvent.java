package com.kok.review.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_review_outbox_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewOutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "outbox_event_id", updatable = false)
    private UUID outboxEventId;

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(name = "store_id",nullable = false)
    private UUID storeId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;          // REVIEW_CREATED / REVIEW_DELETED

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;            // Envelope 전체를 JSON 문자열로 직렬화

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OutboxStatus status;       // PENDING / PUBLISHED / FAILED

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "failed_reason", length = 200)
    private String failedReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    private ReviewOutboxEvent(UUID reviewId, String eventType, String payload, UUID  storeId) {
        this.reviewId = reviewId;
        this.eventType = eventType;
        this.payload = payload;
        this.storeId = storeId;
        this.status = OutboxStatus.PENDING;   // 생성 시 항상 발행 대기
        this.retryCount = 0;
        this.createdAt = LocalDateTime.now();
    }

    //ReviewOutboxEvent 생성
    public static ReviewOutboxEvent create(UUID reviewId, String eventType, String payload, UUID storeId) {
        return ReviewOutboxEvent.builder()
                .reviewId(reviewId)
                .storeId(storeId)
                .eventType(eventType)
                .payload(payload)  //이벤트 타입과 찐 데이터가 담겨 있음.
                .build();
    }

    /** 발행 성공 처리 */
    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /** 발행 실패 처리 */
    public void markFailed(String reason) {
        this.status = OutboxStatus.FAILED;
        this.retryCount += 1;
        this.failedReason = reason != null && reason.length() > 200
                ? reason.substring(0, 200) : reason;
        this.updatedAt = LocalDateTime.now();
    }
}
