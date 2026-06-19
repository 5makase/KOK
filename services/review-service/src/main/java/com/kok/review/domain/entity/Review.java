package com.kok.review.domain.entity;

import com.kok.review.presentation.dto.ReviewRequestDto;
import com.omakase.kok.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "p_reviews")
@Entity
public class Review extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "review_id")
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

    @Column(name = "is_visible")
    private boolean isVisible;               // 노출 여부, 신고 누적 시, 블라인드 처리

    @Column(name = "like_count")
    private int likeCount;                   // 좋아요 수

    //리뷰 생성.
    public static Review create(ReviewRequestDto dto,UUID userId){
        return Review.builder()
                .storeId(dto.getStoreId())
                .userId(userId)
                .reservationId(dto.getReservationId())
                .rating(dto.getRating())
                .content(dto.getContent())
                .build();
    }

}
