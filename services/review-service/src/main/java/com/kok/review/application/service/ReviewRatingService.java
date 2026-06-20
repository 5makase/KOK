package com.kok.review.application.service;

import com.kok.review.domain.entity.ReviewRatingSummary;
import com.kok.review.domain.repository.ReviewRatingSummaryRepository;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewRatingService {
    private final ReviewRatingSummaryRepository reviewRatingSummaryRepository;
    /**
     * 리뷰 추가 시 집계 반영.
     * 별도 트랜잭션(REQUIRES_NEW)으로 분리 — 낙관적 락 재시도가 호출자 트랜잭션을 망가뜨리지 않도록.
     */
    @Retryable(
            retryFor = OptimisticLockException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 50))
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void addRating(UUID storeId, BigDecimal rating) {
        ReviewRatingSummary summary = reviewRatingSummaryRepository.findById(storeId)
                .orElseGet(() -> ReviewRatingSummary.init(storeId));
        summary.addRating(toScaledInt(rating));
        reviewRatingSummaryRepository.save(summary);
    }

    /**
     * 리뷰 삭제 시 집계에서 제외.
     */
    @Retryable(
            retryFor = OptimisticLockException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 50))
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void subtractRating(UUID storeId, BigDecimal rating) {
        ReviewRatingSummary summary = reviewRatingSummaryRepository.findById(storeId)
                .orElseThrow(() -> new IllegalStateException("집계 정보가 없습니다. storeId=" + storeId));
        summary.subtractRating(toScaledInt(rating));
        reviewRatingSummaryRepository.save(summary);
    }

    /** 4.5 → 45 (×10 정수 스케일링) */
    private int toScaledInt(BigDecimal rating) {
        return rating.multiply(BigDecimal.TEN).intValueExact();
    }

}
