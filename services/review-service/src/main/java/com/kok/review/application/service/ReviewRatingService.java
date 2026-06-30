package com.kok.review.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kok.review.domain.entity.ReviewOutboxEvent;
import com.kok.review.domain.entity.ReviewRatingSummary;
import com.kok.review.domain.repository.ReviewOutboxEventRepository;
import com.kok.review.domain.repository.ReviewRatingSummaryRepository;
import com.kok.review.infrastructure.messaging.dto.ReviewEventEnvelope;
import com.kok.review.infrastructure.messaging.dto.ReviewEventPayload;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewRatingService {

    private final ReviewRatingSummaryRepository reviewRatingSummaryRepository;
    private final ReviewOutboxEventRepository reviewOutboxEventRepository;
    private final ObjectMapper objectMapper;

    public void applyUpdated(UUID reviewId, UUID storeId, BigDecimal oldRating, BigDecimal newRating) {
        // 집계 조회 (수정이면 이미 존재해야 정상)
        ReviewRatingSummary summary = reviewRatingSummaryRepository.findById(storeId)
                .orElseThrow(() -> new IllegalStateException("집계 정보가 없습니다. storeId=" + storeId));

        // 기존 별점 빼고 새 별점 더함
        summary.replaceRating(toScaledInt(oldRating), toScaledInt(newRating));
        reviewRatingSummaryRepository.save(summary);

        // 갱신된 최신 평균/개수를 payload에 담음
        ReviewEventPayload payload = ReviewEventPayload.created(   // rating 포함 형태 재사용
                reviewId, storeId, newRating,
                summary.getAverageRating(), summary.getReviewCount());

        saveOutbox(reviewId,"REVIEW_UPDATED", payload);
    }

    /**
     * 리뷰 생성 -> A 가게의 평점에 증가. -> Kafka 이벤트 만듦.
     *
     * @param reviewId
     * @param storeId
     * @param rating
     */
    public void applyCreated(UUID reviewId, UUID storeId, BigDecimal rating) {
        //A가게의 총 평점을 가져옴.
        ReviewRatingSummary summary = getOrCreateSummary(storeId);
        //A 가게의 평점을 증가
        summary.addRating(toScaledInt(rating));
        //DB에 저장.
        reviewRatingSummaryRepository.save(summary);

        //A가게 평점 반영하고 Kafka의 Payload를 만듦.
        ReviewEventPayload payload = ReviewEventPayload.created(
                reviewId, storeId, rating, summary.getAverageRating(), summary.getReviewCount());
        //Kafka 이벤트를 생성함.
        saveOutbox(reviewId, "REVIEW_CREATED", payload);
    }

    /**
     * 리뷰 삭제 시, 그 리뷰는 총 평점에서 제외 -> 카프카 이벤트 발행
     *
     * @param reviewId
     * @param storeId
     * @param rating
     */
    public void applyDeleted(UUID reviewId, UUID storeId, BigDecimal rating) {
        //A가게의 총 평점을 가져옴.
        ReviewRatingSummary summary = reviewRatingSummaryRepository.findById(storeId)
                .orElseThrow(() -> new IllegalStateException("집계 정보가 없습니다. storeId=" + storeId));
        //그 총 평점에서 삭제할 리뷰의 평점을 삭제.
        summary.subtractRating(toScaledInt(rating));
        // DB에 반영
        reviewRatingSummaryRepository.save(summary);

        //삭제 전용 paylaod 생성
        ReviewEventPayload payload = ReviewEventPayload.deleted(
                reviewId, storeId,
                summary.getAverageRating(), summary.getReviewCount());

        //kafka 이벤트 생성
        saveOutbox(reviewId, "REVIEW_DELETED", payload);
    }

    //A가게의 총 평점을 가져온다. - 많이 없을 경우 새롭게 0.00의 평점을 만들어 가져온다.
    private ReviewRatingSummary getOrCreateSummary(UUID storeId) {
        return reviewRatingSummaryRepository.findById(storeId)
                .orElseGet(() -> ReviewRatingSummary.init(storeId));
    }

    /**
     * OutBox 테이블에 저장.
     * @param reviewId
     * @param eventType REVIEW_CREATED, REVIEW_UPDATED, REVIEW_DELETED
     * @param payload
     */
    private void saveOutbox(UUID reviewId, String eventType, ReviewEventPayload payload) {
        /*eventType과 payload로 ReviewEventEnvelope를 생성.*/
        ReviewEventEnvelope envelope = ReviewEventEnvelope.of(eventType, payload);
        /*만든 ReviewEventEnvelope을 json으로 변환.*/
        String json = serialize(envelope);
        /*Outbox 테이블에 저장.*/
        reviewOutboxEventRepository.save(ReviewOutboxEvent.create(reviewId, eventType, json, payload.storeId()));
    }

    //JSON으로 변환해주는..
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