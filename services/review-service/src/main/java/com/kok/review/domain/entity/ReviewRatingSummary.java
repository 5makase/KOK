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
        //A 가게의 평점을 위의 초기값(0.0)으로 새롭게 만듦.
        return ReviewRatingSummary.builder()
                .storeId(storeId)
                .build();
    }

    //리뷰 추가 시 집계 반영
    public void addRating(int scaledRating) {
        //예: scaledRating(15(1.5))를 별점 합계에 합산
        this.ratingSum += scaledRating;
        // 리뷰 개수도 1 증가
        this.reviewCount += 1;
        //별점 개수 추가.
        adjustBucket(scaledRating, 1);
    }

    //리뷰 삭제 시 집계에서 제외
    public void subtractRating(int scaledRating) {
        //정수로 바뀐 개별 평점으 총 평점 합계에 빼기
        this.ratingSum -= scaledRating;
        //리뷰도 1 감소
        this.reviewCount -= 1;
        //별점 개수 빼기
        adjustBucket(scaledRating, -1);

        //리뷰 개수랑 평점이 음수가 된다면, 0으로 수렴.
        if (this.reviewCount < 0) this.reviewCount = 0;
        if (this.ratingSum < 0) this.ratingSum = 0;
    }

    //총 별점 합계를 평균 계산하여 반환
    public BigDecimal getAverageRating() {
        //reviewCount(리뷰개수)가 0이라면
        if (reviewCount == 0) {
            //0.00으로 반환
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        //reviewCount(리뷰개수)가 1이상이라면,
        return BigDecimal.valueOf(ratingSum) /*ratingSum을 BigDecimal객체로 변환*/

                .divide(/*ratingSum 나누기 (10L * reviewCount)을 계산하여 평점 평균을 계산*/
                        BigDecimal.valueOf(10L * reviewCount),
                        2,/*소수점 2자리까지 표현하는데*/
                        RoundingMode.HALF_UP);/*잘리는 부분은 반올림하여 계산한다.*/
    }

    // 개별 별점을 1~5점으로 반올림해서, 그 점수 개수를 1 올리거나(추가) 내림(삭제)
    private void adjustBucket(int scaledRating, int delta) {
        int bucket = Math.round(scaledRating / 10.0f);
        switch (bucket) {
            case 1 -> count1 = Math.max(0, count1 + delta);
            case 2 -> count2 = Math.max(0, count2 + delta);
            case 3 -> count3 = Math.max(0, count3 + delta);
            case 4 -> count4 = Math.max(0, count4 + delta);
            case 5 -> count5 = Math.max(0, count5 + delta);
            default -> { /* 범위 밖이면 무시 */ }
        }
    }
    //기존 별점을 빼고, 새 별점을 추가.
    public void replaceRating(int oldScaled, int newScaled) {
        this.ratingSum = this.ratingSum - oldScaled + newScaled;
        adjustBucket(oldScaled,-1);
        adjustBucket(newScaled, 1);
        if(this.ratingSum < 0 ){
            this.ratingSum = 0;
        }
    }

}
