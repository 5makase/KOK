package com.kok.review.domain.repository;

import com.kok.review.domain.entity.Review;
import com.kok.review.presentation.DTO1.request.ReviewSortType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReviewRepositoryCustom {
    Page<Review> searchReviews(UUID storeId, ReviewSortType sort, boolean photoOnly, Pageable pageable);
    Page<Review>searchMyReviews(UUID userId,  ReviewSortType sort, boolean photoOnly, Pageable pageable);
}
