package com.kok.review.presentation.DTO1.request;

public enum ReviewSortType {
    LATEST,        // 최신순 (createdAt DESC)
    RATING_HIGH,   // 별점 높은순
    RATING_LOW,    // 별점 낮은순
    HELPFUL        // 좋아요순 (likeCount DESC)
}
