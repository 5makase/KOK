package com.kok.review.application.service;

import com.kok.review.domain.entity.Review;
import com.kok.review.domain.entity.ReviewEligibility;
import com.kok.review.domain.entity.ReviewImage;
import com.kok.review.domain.repository.ReviewImageRepository;
import com.kok.review.domain.repository.ReviewRepository;
import com.kok.review.infrastructure.client.StoreClient;
import com.kok.review.infrastructure.client.UserClient;
import com.kok.review.infrastructure.client.dto.StoreResponse;
import com.kok.review.infrastructure.client.dto.UserResponse;
import com.kok.review.infrastructure.persistence.ReviewEligibilityRepository;
import com.kok.review.presentation.DTO1.request.ReviewSortType;
import com.kok.review.presentation.DTO1.request.ReviewUpdateRequestDto;
import com.kok.review.presentation.DTO1.response.ReviewDeletedResponseDto;
import com.kok.review.presentation.DTO1.request.ReviewRequestDto;
import com.kok.review.presentation.DTO1.response.ReviewCreateResponseDto;
import com.kok.review.presentation.DTO1.response.ReviewGetResponseDto;
import com.kok.review.presentation.DTO1.response.ReviewUpdateResponseDto;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewEligibilityRepository reviewEligibilityRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final ReviewRatingService reviewRatingService;
    private final UserClient userClient;
    private final StoreClient storeClient;

    @Transactional(readOnly = true)
    public Page<ReviewGetResponseDto> getReviews(UUID storeId, ReviewSortType sort,
                                                 boolean photoOnly, Pageable pageable) {
        // QueryDSL로 정렬·필터·페이징 조회
        Page<Review> reviews = reviewRepository.searchReviews(storeId, sort, photoOnly, pageable);

        // 각 리뷰를 DTO로 변환 (외부 데이터 없이 from2 사용)
        return reviews.map(review -> {
            List<String> imageUrls = reviewImageRepository.findByReviewReviewId(review.getReviewId())
                    .stream()
                    .map(ReviewImage::getImageUrl)
                    .toList();
            return ReviewGetResponseDto.from2(review, imageUrls);
        });
    }

    @Transactional(readOnly = true)
    public ReviewGetResponseDto getReview(UUID reviewId){
        //reviewId로 Review를 조회한다.
        Review review = reviewRepository.findById(reviewId).orElseThrow(() -> new IllegalArgumentException("조회되는 리뷰가 없음."));

        //reviewId로 ReviewImage를 조회한다.
        List<ReviewImage> reviewImageList =  reviewImageRepository.findByReviewReviewId(reviewId);

        //imageurls로 변경한다.
        List<String> imageUrls = reviewImageList.stream().map(ReviewImage::getImageUrl).collect(Collectors.toList());

        //Review의 userId로 User를 조회한다. -> User의 name을 추출한다.
        String userName = null;
        try{
            UserResponse user = userClient.getUser(review.getUserId());
            userName = user.name();
        }catch (Exception e){
            log.warn("유저 정보 조회 실패. userId={}, reason={}", review.getUserId(), e.getMessage());
            userName = "일반 사용자";
        }

        //Review의 storeId로 Store를 조회한다.- trycatch를 사용한다.
        String storeName = null;
        try{
            StoreResponse store = storeClient.getStore(review.getStoreId());
            storeName = store.name();
        }catch (Exception e){
            log.warn("매장 정보 조회 실패. storeId={}", review.getStoreId());
            storeName = "일반 매장";
        }
        //ReviewGetResponseDto를 만든다.
        return ReviewGetResponseDto.from(review, imageUrls,userName,storeName);
    }

    /**
     * 리뷰 수정
     * @param reviewId
     * @param userId
     * @param dto
     * @return
     */
    @Retryable(
            retryFor = {OptimisticLockException.class, DataIntegrityViolationException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 50)
    )
    @Transactional
    public ReviewUpdateResponseDto updateReview(UUID reviewId, UUID userId,
                                                ReviewUpdateRequestDto dto, String userRole) {
        // 리뷰 존재 확인
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰 없음."));

        // 권한 확인
        if (!userRole.equals("USER")) {
            throw new IllegalArgumentException("사용자 권한 아님.");
        }
        // 작성자 본인 확인
        if (!review.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인 아님.");
        }
        // 평점 재집계용: 덮어쓰기 전에 기존 별점 확보
        BigDecimal oldRating = review.getRating();

        // 리뷰 본문/별점 수정
        review.update(dto);

        //이미지가 있을 때만 아래 로직 수행
        if(dto.getImageUrls() != null) {
            // 이미지 전체 교체: 기존 전부 soft delete → 새로 저장
            List<ReviewImage> oldImages = reviewImageRepository.findByReviewReviewId(reviewId);
            oldImages.forEach(image -> image.delete(userId));

            List<String> newUrls = dto.getImageUrls();
            List<ReviewImage> newImages = new ArrayList<>();
            for (int i = 0; i < newUrls.size(); i++) {
                newImages.add(ReviewImage.of(review, newUrls.get(i), i));
            }
            reviewImageRepository.saveAll(newImages);
        }


        // 평점 재집계 + REVIEW_UPDATED 이벤트 (별점이 실제로 바뀐 경우에만)
        if (dto.getRating().compareTo(oldRating) != 0) {
            reviewRatingService.applyUpdated(
                    review.getReviewId(), review.getStoreId(), oldRating, dto.getRating());
        }

        return ReviewUpdateResponseDto.from(review);
    }

    /**
     * 리뷰 생성
     * 방문 권한 검증 → 리뷰/이미지 저장 → 권한 소진 → 집계 갱신 + Outbox 이벤트 저장.
     * 전 과정이 하나의 트랜잭션. 낙관적 락/중복 키 충돌 시 트랜잭션 전체를 최대 3회 재시도.
     */
    @Retryable(
            retryFor = { OptimisticLockException.class,/*같은 매장에 동시 리뷰가 들어와 집계를 동시 수정할 때 발생.(낙관적락)*/
                    DataIntegrityViolationException.class/*집계 행이 없는 매장에 첫 리뷰가 동시에 들어와 같은 PK로 Insert가 겹칠 때 발생.*/ },
            maxAttempts = 3,
            backoff = @Backoff(delay = 50))
    @Transactional
    public ReviewCreateResponseDto createReview(ReviewRequestDto dto, UUID userId) {
        // 적재된 권한 조회
        ReviewEligibility reviewEligibility = reviewEligibilityRepository
                .findById(dto.getReservationId())
                .orElseThrow(() -> new IllegalArgumentException("적재된 권한이 없음."));

        // 예약자와 리뷰 작성자가 동일한지
        if (!reviewEligibility.getUserId().equals(userId)) {
            throw new IllegalArgumentException("리뷰 작성자와 동일하지 않음.");
        }

        // 예약했던 가게가 맞는지
        if (!reviewEligibility.getStoreId().equals(dto.getStoreId())) {
            throw new IllegalArgumentException("리뷰 작성할 가게와 동일하지 않음.");
        }

        // 중복 리뷰 확인 (1차 방어 — 최종 방어는 Review.reservation_id UNIQUE 제약)
        if (reviewEligibility.isUsed()) {
            throw new IllegalArgumentException("중복된 리뷰");
        }

        // 리뷰 저장 (이미지가 FK로 참조하므로 먼저 저장)
        Review review = Review.create(dto, userId);
        reviewRepository.save(review);

        // 리뷰 이미지 저장 (있을 때만)
        List<String> imageUrls = dto.getImageUrls();
        if (imageUrls != null && !imageUrls.isEmpty()) {
            List<ReviewImage> reviewImageList = new ArrayList<>();
            for (int i = 0; i < imageUrls.size(); i++) {
                reviewImageList.add(ReviewImage.of(review, imageUrls.get(i), i));
            }
            reviewImageRepository.saveAll(reviewImageList);
        }

        // 권한 소진 처리 (재사용 차단)
        reviewEligibility.markAsUsed();

        // 집계 갱신 + REVIEW_CREATED Outbox 이벤트 저장 (같은 트랜잭션)
        reviewRatingService.applyCreated(
                review.getReviewId(), review.getStoreId(), review.getRating());

        return ReviewCreateResponseDto.form(review);
    }

    /**
     * 리뷰 삭제 (Soft Delete)
     * 본인 확인 → 이미지/리뷰 soft delete → 집계 제외 + Outbox 이벤트 저장.
     */
    @Retryable(
            retryFor = { OptimisticLockException.class, DataIntegrityViolationException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 50))
    @Transactional
    public ReviewDeletedResponseDto deleteReview(UUID reviewId, UUID userId) {
        // 리뷰 조회 (@SQLRestriction 으로 이미 삭제된 리뷰는 조회되지 않음 → 재삭제 자동 방지)
        Review review = reviewRepository.findById(reviewId).orElseThrow(() -> new IllegalArgumentException("존재하지 않은 리뷰"));

        // 작성자 본인 확인
        if (!review.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인 아님");
        }

        // 연관 이미지 함께 soft delete
        List<ReviewImage> images = reviewImageRepository.findByReviewReviewId(reviewId);
        images.forEach(image -> image.delete(userId));

        // 리뷰 soft delete
        review.delete(userId);

        // 집계 제외 + REVIEW_DELETED Outbox 이벤트 저장 (같은 트랜잭션)
        reviewRatingService.applyDeleted(review.getReviewId(), review.getStoreId(), review.getRating());

        return ReviewDeletedResponseDto.from(review);
    }
}