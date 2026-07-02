package com.kok.review.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kok.review.domain.entity.ReviewOutboxEvent;
import com.kok.review.domain.entity.ReviewRatingSummary;
import com.kok.review.domain.repository.ReviewOutboxEventRepository;
import com.kok.review.domain.repository.ReviewRatingSummaryRepository;
import com.kok.review.global.exception.ReviewErrorCode;              // 추가
import com.kok.review.infrastructure.messaging.dto.ReviewEventEnvelope;
import com.kok.review.infrastructure.messaging.dto.ReviewEventPayload;
import com.omakase.kok.common.exception.BaseException;               // 추가
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
     * 리뷰 수정 시, 별점
     * @param reviewId  리뷰 Id
     * @param storeId   리뷰의 storeId
     * @param oldRating 기존 리뷰의 별점
     * @param newRating 수정된 리뷰의 새로운 별점
     */
    public void applyUpdated(UUID reviewId, UUID storeId, BigDecimal oldRating, BigDecimal newRating) {
        // A가게의 별점 합계를 조회
        ReviewRatingSummary summary = reviewRatingSummaryRepository.findById(storeId)
                .orElseThrow(() -> new BaseException(ReviewErrorCode.RATING_SUMMARY_NOT_FOUND));
        // 총 별점 합계에 기존 별점을 제외하고, 새로운 별점을 추가
        summary.replaceRating(toScaledInt(oldRating), toScaledInt(newRating));
        // 변경된 내용을 DB에 저장
        reviewRatingSummaryRepository.save(summary);

        // 카프카에 실을 paylaod를 구성
        ReviewEventPayload payload = ReviewEventPayload.created(
                reviewId,
                storeId,
                newRating,
                summary.getAverageRating(),/*계산된 별점 합계에서 평균을 계산*/
                summary.getReviewCount());

        // OutBox 테이블에 저장
        saveOutbox(reviewId, "REVIEW_UPDATED", payload);
    }

    /**
     * 리뷰 등록 시, 별점 반영
     * @param reviewId 리뷰 ID
     * @param storeId  리뷰의 storeId
     * @param rating   반영할 별점
     */
    public void applyCreated(UUID reviewId, UUID storeId, BigDecimal rating) {
        // A가게의 별점 합계를 조회
        ReviewRatingSummary summary = getOrCreateSummary(storeId);
        // 총 별점 합계에 반영할 전달받은 별점을 추가
        summary.addRating(toScaledInt(rating));
        // DB에 우선 저장
        reviewRatingSummaryRepository.save(summary);

        // 카프카에 실을 paylaod를 구성
        ReviewEventPayload payload = ReviewEventPayload.created(
                reviewId,
                storeId,
                rating,/*추가할 별점*/
                summary.getAverageRating(),/*계산된 별점 합계에서 평균을 계산*/
                summary.getReviewCount());

        //OutBox 테이블에 저장
        saveOutbox(reviewId, "REVIEW_CREATED", payload);
    }

    /**
     * 리뷰 삭제 시 별점 반영
     * @param reviewId 리뷰 ID
     * @param storeId  리뷰의 storeId
     * @param rating   반영할 별점
     */
    public void applyDeleted(UUID reviewId, UUID storeId, BigDecimal rating) {
        //A가게의 별점 합계를 조회
        ReviewRatingSummary summary = reviewRatingSummaryRepository.findById(storeId)
                .orElseThrow(() -> new BaseException(ReviewErrorCode.RATING_SUMMARY_NOT_FOUND));

        //총 별점 합계에서 전달받은 별점을 제외
        summary.subtractRating(toScaledInt(rating));
        //DB에 반영
        reviewRatingSummaryRepository.save(summary);

        //카프카에 실을 paylaod를 생성
        ReviewEventPayload payload = ReviewEventPayload.deleted(
                reviewId,
                storeId,
                summary.getAverageRating(),/*계산된 별점 합계에서 평균을 계산*/
                summary.getReviewCount());

        //Outbox 테이블에 저장
        saveOutbox(reviewId, "REVIEW_DELETED", payload);
    }

    /**
     * A가게의 별점 합계를 조회, 없으면 0.00으로 계산
     * @param storeId
     * @return
     */
    private ReviewRatingSummary getOrCreateSummary(UUID storeId) {
        return reviewRatingSummaryRepository.findById(storeId)
                .orElseGet(() -> ReviewRatingSummary.init(storeId));
    }

    /**
     * OutBox 테이블에 저장
     * @param reviewId
     * @param eventType
     * @param payload
     */
    private void saveOutbox(UUID reviewId, String eventType, ReviewEventPayload payload) {
        ReviewEventEnvelope envelope = ReviewEventEnvelope.of(eventType, payload);
        String json = serialize(envelope);
        reviewOutboxEventRepository.save(
                ReviewOutboxEvent.create(reviewId, eventType, json, payload.storeId()));
    }

    /**
     *  직렬화
     * @param envelope
     * @return
     */
    private String serialize(ReviewEventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new BaseException(ReviewErrorCode.EVENT_SERIALIZATION_FAILED, e); // cause 보존
        }
    }

    /**
     * 1.5 -> 15로 변경
     * @param rating
     * @return
     */
    private int toScaledInt(BigDecimal rating) {
        return rating.multiply(BigDecimal.TEN).intValueExact();
    }
}