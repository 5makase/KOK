package com.kok.review.presentation.controller;

import com.kok.review.application.service.ReviewService;
import com.kok.review.domain.entity.Review;
import com.kok.review.presentation.dto.ReviewRequestDto;
import com.kok.review.presentation.dto.ReviewResponseDto;
import com.omakase.kok.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    /**
     * 리뷰 삭제
     * @param reviewId
     * @param userId
     * @return
     */
    @PatchMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable UUID reviewId, @RequestHeader("X-User-Id")UUID userId) {
        reviewService.deleteReview(reviewId, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 리뷰 생성
     * @param requestDto
     * @return
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponseDto>> createReview(@Valid @RequestBody ReviewRequestDto requestDto, @RequestHeader("X-User-Id")UUID userId) {
        ReviewResponseDto reviewResponseDto = reviewService.createReview(requestDto,userId);
        return ResponseEntity.ok(ApiResponse.success(reviewResponseDto));
    }
}
