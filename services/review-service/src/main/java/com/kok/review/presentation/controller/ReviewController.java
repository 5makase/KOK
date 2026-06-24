package com.kok.review.presentation.controller;

import com.kok.review.application.service.ReviewService;
import com.kok.review.presentation.DTO1.request.ReviewSortType;
import com.kok.review.presentation.DTO1.request.ReviewUpdateRequestDto;
import com.kok.review.presentation.DTO1.response.ReviewDeletedResponseDto;
import com.kok.review.presentation.DTO1.request.ReviewRequestDto;
import com.kok.review.presentation.DTO1.response.ReviewCreateResponseDto;
import com.kok.review.presentation.DTO1.response.ReviewGetResponseDto;
import com.kok.review.presentation.DTO1.response.ReviewUpdateResponseDto;
import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReviewGetResponseDto>>> getReviews(@RequestParam UUID storeId,
                                                                                      @RequestParam(defaultValue = "LATEST") ReviewSortType sort,
                                                                                      @RequestParam(defaultValue = "false") boolean photoOnly,
                                                                                      @RequestParam(defaultValue = "0") int page,
                                                                                      @RequestParam(defaultValue = "10") int size){

        int safeSize = Math.min(size, 50);   // 스펙: 최대 50
        Pageable pageable = PageRequest.of(page, safeSize);

        Page<ReviewGetResponseDto> result = reviewService.getReviews(storeId, sort, photoOnly, pageable);

        return ResponseEntity.ok(
                ApiResponse.success(PageResponse.from(result)));   // Page → PageResponse
    }
    /**
     * 리뷰 상세 조회
     * @param reviewId
     * @return
     */
    @GetMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<ReviewGetResponseDto>> getReview(@PathVariable UUID reviewId) {
        ReviewGetResponseDto reviewGetResponseDto = reviewService.getReview(reviewId);
        return ResponseEntity.ok(ApiResponse.success(reviewGetResponseDto));
    }

    /**
     * 리뷰 수정
     * @param reviewId
     * @param userId
     * @param dto
     * @param userRole
     * @return
     */
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
    public ResponseEntity<ApiResponse<ReviewCreateResponseDto>> createReview(@Valid @RequestBody ReviewRequestDto requestDto, @RequestHeader("X-User-Id")UUID userId) {
        ReviewCreateResponseDto reviewResponseDto = reviewService.createReview(requestDto,userId);
        return ResponseEntity.ok(ApiResponse.success(reviewResponseDto));
    }
}
