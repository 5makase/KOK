package com.kok.review.application.service;

import com.kok.review.domain.entity.Review;
import com.kok.review.domain.entity.ReviewReply;
import com.kok.review.domain.repository.ReviewReplyRepository;
import com.kok.review.domain.repository.ReviewRepository;
import com.kok.review.infrastructure.client.StoreClient;
import com.kok.review.infrastructure.client.dto.StoreResponse;
import com.kok.review.presentation.DTO1.request.ReviewReplyRequestDto;
import com.kok.review.presentation.DTO1.response.ReviewReplyResponseDto;
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

    /**
     * 사장님 답글 작성
     * @param reviewId 어떤 리뷰에 답글을 달았는지
     * @param userId  사장님의 userId는 무엇인지
     * @param userRole 사장님의 권한은 무엇인지
     * @param requestDto 사장님의 content는 무엇인지
     * @return
     */
    @Transactional
    public ReviewReplyResponseDto createReply(UUID reviewId, UUID userId,String userRole, ReviewReplyRequestDto requestDto ) {
        //사장님 권한 확인
        if(!"OWNER".equals(userRole)) {
            throw new IllegalArgumentException("사장님 아님.");
        }
        //리뷰가 존재하는 지 확인
        Review review = reviewRepository.findById(reviewId).orElseThrow(() -> new IllegalArgumentException("리뷰가 존재하지 않음."));
        //Review.storeId로 가게를 조회하여
        StoreResponse store = storeClient.getStore(review.getStoreId());
        //Store.ownerId와 userId가 동일한지 확인
        if(!store.ownerId().equals(userId)) {
            throw new IllegalArgumentException("해당 매장의 소유주가 아님");
        }
        //reviewId로 ReviewReply가 있는지 확인 - 리뷰 1개에 1의 답글만 달도록 - 애플리케이션 단에서 방어
        if(reviewReplyRepository.existsByReviewId(reviewId)) {
            throw new IllegalArgumentException("이미 답글이 존재함.");
        }
        //ReviewReply를 DB에 저장
        ReviewReply reviewReply = ReviewReply.create(reviewId, review.getStoreId(),userId, requestDto.getContent());
        reviewReplyRepository.save(reviewReply);
        return ReviewReplyResponseDto.from(reviewReply);
    }

    /**
     * 사장님 답글 수정
     * @param reviewId
     * @param userId
     * @param userRole
     * @param requestDto
     * @return
     */
    public ReviewReplyResponseDto updateReply(UUID reviewId, UUID userId, String userRole, ReviewReplyRequestDto requestDto ) {
        //사장님 권한인지 확인
        if (!"OWNER".equals(userRole)) {
            throw new IllegalArgumentException("사장님 권한 아님.");
        }
        //사장님 답글 조회
        ReviewReply reply = reviewReplyRepository.findByReviewId(reviewId);
        if(reply == null){
            throw new IllegalArgumentException("답글이 존재하지 않음.");
        }

        // 작성한 본인 확인
        if (!reply.getOwnerId().equals(userId)) {
            throw new IllegalArgumentException("본인 답글 아님.");
        }
        reply.update(requestDto);
        return ReviewReplyResponseDto.from(reply);
    }

    /**
     * 사장님 답글 삭제
     * @param reviewId
     * @param userId
     * @param userRole
     */
    @Transactional
    public void deleteReply(UUID reviewId, UUID userId, String userRole) {
        //본인 권한 확인
        if (!"OWNER".equals(userRole)) {
            throw new IllegalArgumentException("사장님 권한 아님.");
        }
        //본인 답글 조회
        ReviewReply reply = reviewReplyRepository.findByReviewId(reviewId);

        //답글 조회 안될 시, 예외 처리
        if(reply == null){
            throw new IllegalArgumentException("답글이 존재하지 않음.");
        }
        //본인 답글인지 확인
        if (!reply.getOwnerId().equals(userId)) {
            throw new IllegalArgumentException("본인 답글 아님.");
        }
        //soft delete 처리
        reply.delete(userId);
    }


}
