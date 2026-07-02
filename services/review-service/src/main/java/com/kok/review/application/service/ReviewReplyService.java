package com.kok.review.application.service;

import com.kok.review.domain.entity.Review;
import com.kok.review.domain.entity.ReviewReply;
import com.kok.review.domain.repository.ReviewReplyRepository;
import com.kok.review.domain.repository.ReviewRepository;
import com.kok.review.global.exception.ReviewErrorCode;
import com.kok.review.infrastructure.client.StoreClient;
import com.kok.review.infrastructure.client.dto.StoreResponse;
import com.kok.review.infrastructure.messaging.dto.ReviewReplyPayload;
import com.kok.review.presentation.DTO1.request.ReviewReplyRequestDto;
import com.kok.review.presentation.DTO1.response.ReviewReplyResponseDto;
import com.omakase.kok.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewReplyService {
    private final ReviewReplyRepository reviewReplyRepository;
    private final ReviewRepository reviewRepository;
    private final StoreClient storeClient;
    private final ReviewOutboxAppender reviewOutboxAppender;

    /**
     * 사장님 답글 작성
     */
    @Transactional
    public ReviewReplyResponseDto createReply(UUID reviewId, UUID userId, String userRole,
                                              ReviewReplyRequestDto requestDto) {
        // 사장님 권한 확인
        if (!"OWNER".equals(userRole)) {
            throw new BaseException(ReviewErrorCode.NOT_OWNER_ROLE);
        }
        // 리뷰가 존재하는지 확인
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BaseException(ReviewErrorCode.REVIEW_NOT_FOUND));

        // Review.storeId로 가게를 조회
        StoreResponse store = storeClient.getStore(review.getStoreId());

        // Store.ownerId와 userId가 동일한지 확인
        if (!store.ownerId().equals(userId)) {
            throw new BaseException(ReviewErrorCode.NOT_STORE_OWNER);
        }

        // 리뷰당 답글 1개 (애플리케이션 1차 방어, 최종 방어는 review_id UNIQUE)
        if (reviewReplyRepository.existsByReviewId(reviewId)) {
            throw new BaseException(ReviewErrorCode.REPLY_ALREADY_EXISTS);
        }

        // ReviewReply 저장
        ReviewReply reviewReply = ReviewReply.create(
                reviewId, review.getStoreId(), userId, requestDto.getContent());
        reviewReplyRepository.save(reviewReply);

        // REVIEW_REPLY 이벤트 발행 (같은 트랜잭션 Outbox)
        ReviewReplyPayload payload = ReviewReplyPayload.of(
                review.getReviewId(),
                review.getStoreId(),
                store.storeName(),
                review.getUserId(),    // 알림 수신자(리뷰 작성자)
                reviewReply.getContent()
        );
        reviewOutboxAppender.append(
                review.getReviewId(), "REVIEW_REPLY", review.getStoreId(), payload);

        return ReviewReplyResponseDto.from(reviewReply);
    }

    /**
     * 사장님 답글 수정
     */
    @Transactional
    public ReviewReplyResponseDto updateReply(UUID reviewId, UUID userId, String userRole,
                                              ReviewReplyRequestDto requestDto) {
        if (!"OWNER".equals(userRole)) {
            throw new BaseException(ReviewErrorCode.NOT_OWNER_ROLE);
        }
        ReviewReply reply = reviewReplyRepository.findByReviewId(reviewId);
        if (reply == null) {
            throw new BaseException(ReviewErrorCode.REPLY_NOT_FOUND);
        }
        // 작성한 본인 확인
        if (!reply.getOwnerId().equals(userId)) {
            throw new BaseException(ReviewErrorCode.NOT_REPLY_AUTHOR);
        }
        reply.update(requestDto);
        return ReviewReplyResponseDto.from(reply);
    }

    /**
     * 사장님 답글 삭제 (Soft Delete)
     */
    @Transactional
    public void deleteReply(UUID reviewId, UUID userId, String userRole) {
        if (!"OWNER".equals(userRole)) {
            throw new BaseException(ReviewErrorCode.NOT_OWNER_ROLE);
        }
        ReviewReply reply = reviewReplyRepository.findByReviewId(reviewId);
        if (reply == null) {
            throw new BaseException(ReviewErrorCode.REPLY_NOT_FOUND);
        }
        if (!reply.getOwnerId().equals(userId)) {
            throw new BaseException(ReviewErrorCode.NOT_REPLY_AUTHOR);
        }
        reply.delete(userId);
    }
}