package com.kok.review.application.service;

import com.kok.review.domain.entity.Review;
import com.kok.review.domain.entity.ReviewEligibility;
import com.kok.review.domain.entity.ReviewImage;
import com.kok.review.domain.repository.ReviewImageRepository;
import com.kok.review.domain.repository.ReviewRepository;
import com.kok.review.infrastructure.persistence.ReviewEligibilityRepository;
import com.kok.review.presentation.dto.ReviewRequestDto;
import com.kok.review.presentation.dto.ReviewResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final ReviewEligibilityRepository reviewEligibilityRepository;
    private final ReviewImageRepository reviewImageRepository;

    /**
     * 리뷰 생성
     *
     * @param dto
     * @return
     */
    @Transactional
    public ReviewResponseDto createReview(ReviewRequestDto dto, UUID userId) {
        //적재된 권한을 가져옴.
        ReviewEligibility reviewEligibility = reviewEligibilityRepository.findById(dto.getReservationId()).orElseThrow(() -> new IllegalArgumentException("적재된 권한이 없음."));

        //예약자와 리뷰 작성자가 동일한지
        if (!reviewEligibility.getUserId().equals(userId)) {
            throw new IllegalArgumentException("리뷰 작성자와 동일하지 않음.");
        }

        //예약했던 가게가 맞는지
        if (!reviewEligibility.getStoreId().equals(dto.getStoreId())) {
            throw new IllegalArgumentException("리뷰 작성할 가게와 동일하지 않음.");
        }

        //중복리뷰 확인.
        if (reviewEligibility.isUsed()) {
            throw new IllegalArgumentException("중복된 리뷰");
        }

        //리뷰 생성
        Review review = Review.create(dto, userId);
        reviewRepository.save(review);

        // 리뷰 이미지 저장.
            //이미지 url 묶음을 꺼냄
        List<String> imageUrls = dto.getImageUrls();
            //이미지 URL 묶음들이 실제로 존재하는지 확인.
        if (imageUrls != null && !imageUrls.isEmpty()) {
                //DB에 저장시킬 ReviewImage 리스트를 생성.
            List<ReviewImage> reviewImageList = new ArrayList<>();
                // 이미지 URR 묶음들 크기만큼 반복하여
            for (int i = 0; i < imageUrls.size(); i++) {
                    //ReviewImage 리스트에 ReviewImage를 추가.(리뷰, 리뷰 이미지 1장, 순서)
                reviewImageList.add(ReviewImage.of(review,imageUrls.get(i),i));
            }
                //ReviewImage를 한번에 저장.
            reviewImageRepository.saveAll(reviewImageList);
        }
        //중복처리
        reviewEligibility.markAsUsed();

        //반환객체로 변환
        return ReviewResponseDto.form(review);
    }
}
