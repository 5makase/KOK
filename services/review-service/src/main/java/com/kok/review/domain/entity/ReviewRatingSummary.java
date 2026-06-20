package com.kok.review.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_review_rating_summaries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewRatingSummary {
    @Id
    @Column(name = "store_id", updatable = false)
    private UUID storeId;

    @Column(name = "rating_sum", nullable = false)
    private long ratingSum;          // 별점 합계 (×10 정수 스케일. 예: 4.5 → 45)

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    // 별점 분포 (정수 버킷 1~5). 0.5점은 반올림하여 해당 버킷에 포함
    @Column(name = "count_1", nullable = false)
    private int count1;
    @Column(name = "count_2", nullable = false)
    private int count2;
    @Column(name = "count_3", nullable = false)
    private int count3;
    @Column(name = "count_4", nullable = false)
    private int count4;
    @Column(name = "count_5", nullable = false)
    private int count5;

    @Version
    @Column(name = "version", nullable = false)
    private long version;            // 낙관적 락

    @Builder
    private ReviewRatingSummary(UUID storeId) {
        this.storeId = storeId;
        this.ratingSum = 0L;
        this.reviewCount = 0;
        this.count1 = 0;
        this.count2 = 0;
        this.count3 = 0;
        this.count4 = 0;
        this.count5 = 0;
    }

    //매장별 집계 최초 생성 - 0.0으로 시작함.
    public static ReviewRatingSummary init(UUID storeId) {
        return ReviewRatingSummary.builder()
                .storeId(storeId)
                .build();
    }

    //리뷰 추가 시 집계 반영
    public void addRating(int scaledRating) {
        this.ratingSum += scaledRating;
        this.reviewCount += 1;
        adjustBucket(scaledRating, 1);
    }

    //리뷰 삭제 시 집계에서 제외
    public void subtractRating(int scaledRating) {
        this.ratingSum -= scaledRating;
        this.reviewCount -= 1;
        adjustBucket(scaledRating, -1);
        // 방어: 음수 방지 (정합성 안전장치)
        if (this.reviewCount < 0) this.reviewCount = 0;
        if (this.ratingSum < 0) this.ratingSum = 0;
    }

    //평균 평점을 DECIMAL(3,2)로 반환
    public BigDecimal getAverageRating() {
        if (reviewCount == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(ratingSum)
                .divide(BigDecimal.valueOf(10L * reviewCount), 2, RoundingMode.HALF_UP);
    }
   //scaledRating(10~50)을 정수 버킷(1~5)에 매핑하여 delta(+1/-1)만큼 가감
    private void adjustBucket(int scaledRating, int delta) {
        int bucket = Math.round(scaledRating / 10.0f);  // 45 → 4.5 → 5, 25 → 2.5 → 3
        switch (bucket) {
            case 1 -> count1 += delta;
            case 2 -> count2 += delta;
            case 3 -> count3 += delta;
            case 4 -> count4 += delta;
            case 5 -> count5 += delta;
            default -> { /* 범위 밖이면 무시 (방어) */ }
        }
    }

}
