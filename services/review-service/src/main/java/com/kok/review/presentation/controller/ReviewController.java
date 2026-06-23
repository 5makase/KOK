package com.kok.review.presentation.controller;

import com.kok.review.application.service.ReviewService;
import com.kok.review.presentation.DTO1.request.ReviewUpdateRequestDto;
import com.kok.review.presentation.DTO1.response.ReviewDeletedResponseDto;
import com.kok.review.presentation.DTO1.request.ReviewRequestDto;
import com.kok.review.presentation.DTO1.response.ReviewResponseDto;
import com.kok.review.presentation.DTO1.response.ReviewUpdateResponseDto;
import com.omakase.kok.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @PutMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<ReviewUpdateResponseDto>> updateReview(@PathVariable UUID reviewId,
                                                                             @RequestHeader("X-User-Id")UUID userId,
                                                                             @Valid @RequestBody ReviewUpdateRequestDto dto,
                                                                             @RequestHeader("X-User-Role") String userRole) {
        ReviewUpdateResponseDto reviewUpdateResponseDto =  reviewService.updateReview(reviewId,userId,dto,userRole);
        return ResponseEntity.ok(ApiResponse.success(reviewUpdateResponseDto));
    }

    /**
     * 리뷰 삭제
     * @param reviewId
     * @param userId
     * @return
     */
    @PatchMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<ReviewDeletedResponseDto>> deleteReview(@PathVariable UUID reviewId, @RequestHeader("X-User-Id")UUID userId) {
        ReviewDeletedResponseDto dto = reviewService.deleteReview(reviewId, userId);
        return ResponseEntity.ok(ApiResponse.success(dto));
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
