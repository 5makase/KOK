//package com.kok.review;
//
//import com.kok.review.domain.entity.ReviewRatingSummary;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//
//import java.math.BigDecimal;
//import java.util.UUID;
//import static org.assertj.core.api.Assertions.assertThat;
//
//class ReviewRatingSummaryTest {
//
//    @Test
//    @DisplayName("리뷰 추가 시 평점 합계와 개수, 별점 분포가 정확히 갱신된다")
//    void addRating_test() {
//        // Given
//        UUID storeId = UUID.randomUUID();
//        ReviewRatingSummary summary = ReviewRatingSummary.init(storeId);
//
//        // When: 5점(50)과 4점(40) 리뷰 추가
//        summary.addRating(50);
//        summary.addRating(40);
//
//        // Then
//        assertThat(summary.getReviewCount()).isEqualTo(2);
//        assertThat(summary.getRatingSum()).isEqualTo(90L); // 50 + 40 = 90
//        assertThat(summary.getCount5()).isEqualTo(1);
//        assertThat(summary.getCount4()).isEqualTo(1);
//
//        // 평균: 90 / (10 * 2) = 4.50
//        assertThat(summary.getAverageRating()).isEqualTo(new BigDecimal("4.50"));
//    }
//
//    @Test
//    @DisplayName("리뷰 삭제 시 평점 데이터가 올바르게 차감된다")
//    void subtractRating_test() {
//        // Given
//        UUID storeId = UUID.randomUUID();
//        ReviewRatingSummary summary = ReviewRatingSummary.init(storeId);
//        summary.addRating(50); // 5점
//        summary.addRating(30); // 3점
//
//        // When: 5점 리뷰 삭제
//        summary.subtractRating(50);
//
//        // Then
//        assertThat(summary.getReviewCount()).isEqualTo(1);
//        assertThat(summary.getRatingSum()).isEqualTo(30L);
//        assertThat(summary.getCount5()).isEqualTo(0);
//        assertThat(summary.getAverageRating()).isEqualTo(new BigDecimal("3.00"));
//    }
//}