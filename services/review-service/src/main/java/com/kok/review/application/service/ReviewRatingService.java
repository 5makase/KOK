package com.kok.review.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kok.review.domain.entity.ReviewOutboxEvent;
import com.kok.review.domain.entity.ReviewRatingSummary;
import com.kok.review.domain.repository.ReviewOutboxEventRepository;
import com.kok.review.domain.repository.ReviewRatingSummaryRepository;
import com.kok.review.infrastructure.messaging.dto.ReviewEventEnvelope;
import com.kok.review.infrastructure.messaging.dto.ReviewEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewRatingService {

    private final ReviewRatingSummaryRepository reviewRatingSummaryRepository;
    private final ReviewOutboxEventRepository reviewOutboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * 리뷰 생성 시: 집계 반영 + REVIEW_CREATED Outbox 이벤트 저장.
     * 호출자(createReview) 트랜잭션에 합류 — 별도 @Transactional 없음.
     */
    public void applyCreated(UUID reviewId, UUID storeId, BigDecimal rating) {
        ReviewRatingSummary summary = getOrCreateSummary(storeId);
        summary.addRating(toScaledInt(rating));
        reviewRatingSummaryRepository.save(summary);

        // 집계 갱신 후의 최신 평균/개수를 payload에 담음 (스냅샷)
        ReviewEventPayload payload = ReviewEventPayload.created(
                reviewId, storeId, rating,
                summary.getAverageRating(), summary.getReviewCount());

        saveOutbox(reviewId, "REVIEW_CREATED", payload);
    }

    /**
     * 리뷰 삭제 시: 집계 제외 + REVIEW_DELETED Outbox 이벤트 저장.
     */
    public void applyDeleted(UUID reviewId, UUID storeId, BigDecimal rating) {
        ReviewRatingSummary summary = reviewRatingSummaryRepository.findById(storeId)
                .orElseThrow(() -> new IllegalStateException("집계 정보가 없습니다. storeId=" + storeId));
        summary.subtractRating(toScaledInt(rating));
        reviewRatingSummaryRepository.save(summary);

        ReviewEventPayload payload = ReviewEventPayload.deleted(
                reviewId, storeId,
                summary.getAverageRating(), summary.getReviewCount());

        saveOutbox(reviewId, "REVIEW_DELETED", payload);
    }

    private ReviewRatingSummary getOrCreateSummary(UUID storeId) {
        return reviewRatingSummaryRepository.findById(storeId)
                .orElseGet(() -> ReviewRatingSummary.init(storeId));
    }

    private void saveOutbox(UUID reviewId, String eventType, ReviewEventPayload payload) {
        ReviewEventEnvelope envelope = ReviewEventEnvelope.of(eventType, payload);
        String json = serialize(envelope);
        reviewOutboxEventRepository.save(
                ReviewOutboxEvent.create(reviewId, eventType, json));
    }

    private String serialize(ReviewEventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("이벤트 직렬화 실패", e);
        }
    }

    private int toScaledInt(BigDecimal rating) {
        return rating.multiply(BigDecimal.TEN).intValueExact();
    }
}