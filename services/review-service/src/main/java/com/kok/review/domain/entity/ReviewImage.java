package com.kok.review.domain.entity;

import com.omakase.kok.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_review_images")
@SQLRestriction("deleted_at IS NULL")  // 조회 시 soft delete 자동 필터링
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "image_id", updatable = false)
    private UUID imageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    // 사진을 여러 장 보여줄 때, 어떤 순서로 보여줄지
    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Builder
    private ReviewImage(Review review, String imageUrl, int displayOrder) {
        this.review = review;
        this.imageUrl = imageUrl;
        this.displayOrder = displayOrder;
    }

    // 리뷰 이미지 생성
    public static ReviewImage of(Review review, String imageUrl, int displayOrder) {
        return ReviewImage.builder()
                .review(review)
                .imageUrl(imageUrl)
                .displayOrder(displayOrder)
                .build();
    }

    // 노출 순서 변경
    public void changeDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
