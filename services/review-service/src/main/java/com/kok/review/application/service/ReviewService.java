package com.kok.review.application.service;

import com.kok.review.domain.entity.Review;
import com.kok.review.domain.entity.ReviewEligibility;
import com.kok.review.domain.repository.ReviewRepository;
import com.kok.review.infrastructure.persistence.ReviewEligibilityRepository;
import com.kok.review.presentation.dto.ReviewRequestDto;
import com.kok.review.presentation.dto.ReviewResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final ReviewEligibilityRepository reviewEligibilityRepository;

    /**
     * 리뷰 생성
     * @param dto
     * @return
     */
    @Transactional
    public ReviewResponseDto createReview(ReviewRequestDto dto, UUID userId) {
        //적재된 권한을 가져옴.
        ReviewEligibility reviewEligibility = reviewEligibilityRepository.findById(dto.getReservationId()).orElseThrow(() -> new IllegalArgumentException("적재된 권한이 없음."));

        //예약자와 리뷰 작성자가 동일한지
        if(!reviewEligibility.getUserId().equals(userId)) {
            throw new IllegalArgumentException("리뷰 작성자와 동일하지 않음.");
        }

        //예약했던 가게가 맞는지
        if(!reviewEligibility.getStoreId().equals(dto.getStoreId())) {
            throw new IllegalArgumentException("리뷰 작성할 가게와 동일하지 않음.");
        }

        //중복리뷰 확인.
        if(reviewEligibility.isUsed()) {
            throw new IllegalArgumentException("중복된 리뷰");
        }

        //리뷰 저장.
        Review review = Review.create(dto,userId);
        reviewRepository.save(review);

        //중복처리
        reviewEligibility.markAsUsed();

        //반환객체로 변환
        return ReviewResponseDto.form(review);
    }
}
