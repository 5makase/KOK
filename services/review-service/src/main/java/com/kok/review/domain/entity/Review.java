package com.kok.review.domain.entity;

import com.kok.review.presentation.dto.ReviewRequestDto;
import com.omakase.kok.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_reviews")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")  // 조회 시 soft delete 자동 필터링
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "review_id", updatable = false)
    private UUID reviewId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal rating;               // 별점 1.0 ~ 5.0 (0.5 단위)

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;                  // 리뷰 본문

    @Column(name = "is_visible", nullable = false)
    private boolean isVisible;               // 노출 여부, 신고 누적 시 블라인드 처리

    @Column(name = "like_count", nullable = false)
    private int likeCount;                   // 좋아요 수

    @Builder
    private Review(UUID storeId, UUID userId, UUID reservationId,
                   BigDecimal rating, String content) {
        this.storeId = storeId;
        this.userId = userId;
        this.reservationId = reservationId;
        this.rating = rating;
        this.content = content;
        this.isVisible = true;   // 생성 시 기본 노출
        this.likeCount = 0;      // 생성 시 좋아요 0
    }

    // 리뷰 생성
    public static Review create(ReviewRequestDto dto, UUID userId) {
        return Review.builder()
                .storeId(dto.getStoreId())
                .userId(userId)
                .reservationId(dto.getReservationId())
                .rating(dto.getRating())
                .content(dto.getContent())
                .build();
    }
}