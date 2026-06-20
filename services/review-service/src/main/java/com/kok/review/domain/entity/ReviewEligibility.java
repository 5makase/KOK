package com.kok.review.domain.entity;

import com.kok.review.infrastructure.messaging.dto.ReservationEvent;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_review_eligibilities",
        uniqueConstraints = @UniqueConstraint(columnNames = "event_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewEligibility {

    @Id
    @Column(name = "reservation_id")
    private UUID reservationId;       // PK 겸 중복 리뷰 방지 키

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private LocalDateTime visitedAt;

    @Column(nullable = false)
    private boolean isUsed;           // 리뷰 작성 완료 시 true

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;             // 멱등 처리용

    @Builder
    private ReviewEligibility(UUID reservationId, UUID storeId, UUID userId,
                              LocalDateTime visitedAt, UUID eventId) {
        this.reservationId = reservationId;
        this.storeId = storeId;
        this.userId = userId;
        this.visitedAt = visitedAt;
        this.eventId = eventId;
        this.isUsed = false;          // 생성 시 항상 미사용 상태
    }

    public static ReviewEligibility create(ReservationEvent event) {
        return ReviewEligibility.builder()
                .eventId(event.eventId())
                .reservationId(event.reservationId())
                .storeId(event.storeId())
                .userId(event.userId())
                .visitedAt(event.visitedAt())
                .build();
    }

    // 멱등 처리
    public void markAsUsed() {
        this.isUsed = true;
    }
}