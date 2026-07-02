package com.kok.review.presentation.controller;

import com.kok.review.application.service.ReviewReplyService;
import com.kok.review.application.service.ReviewService;
import com.kok.review.presentation.DTO1.request.ReviewReplyRequestDto;
import com.kok.review.presentation.DTO1.request.ReviewSortType;
import com.kok.review.presentation.DTO1.request.ReviewUpdateRequestDto;
import com.kok.review.presentation.DTO1.response.*;
import com.kok.review.presentation.DTO1.request.ReviewRequestDto;
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
    private final ReviewReplyService reviewReplyService;


    /**
     * 사장님 답글 작성
     * @param reviewId
     * @param userId
     * @param userRole
     * @param dto
     * @return
     */
    @PostMapping("/{reviewId}/replies")
    public ResponseEntity<ApiResponse<ReviewReplyResponseDto>> createReply(
            @PathVariable UUID reviewId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-Role") String userRole,
            @Valid @RequestBody ReviewReplyRequestDto dto) {
        ReviewReplyResponseDto result = reviewReplyService.createReply(reviewId, userId, userRole, dto);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 사장님 답글 수정
     * @param reviewId
     * @param userId
     * @param userRole
     * @param dto
     * @return
     */
    @PutMapping("/{reviewId}/replies")
    public ResponseEntity<ApiResponse<ReviewReplyResponseDto>> updateReply(
            @PathVariable UUID reviewId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-Role") String userRole,
            @Valid @RequestBody ReviewReplyRequestDto dto) {
        ReviewReplyResponseDto result = reviewReplyService.updateReply(reviewId, userId, userRole, dto);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 사장님 답글 삭제
     * @param reviewId
     * @param userId
     * @param userRole
     * @return
     */
    @PatchMapping("/{reviewId}/replies")
    public ResponseEntity<ApiResponse<Void>> deleteReply(
            @PathVariable UUID reviewId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-Role") String userRole) {
        reviewReplyService.deleteReply(reviewId, userId, userRole);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 내 리뷰 조회
     * @param sort
     * @param photoOnly
     * @param page
     * @param size
     * @return
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<ReviewGetResponseDto>>> getMyReviews(@RequestHeader("X-User-Id") UUID userId,
                                                                                      @RequestParam(defaultValue = "LATEST") ReviewSortType sort,
                                                                                      @RequestParam(defaultValue = "false") boolean photoOnly,
                                                                                      @RequestParam(defaultValue = "0") int page,
                                                                                      @RequestParam(defaultValue = "10") int size){
        int safeSize = Math.min(size, 50);   //size ~ 최대 50개 까지
        Pageable pageable = PageRequest.of(page, safeSize);
        Page<ReviewGetResponseDto> result = reviewService.getMyReviews(userId, sort, photoOnly, pageable);
        return ResponseEntity.ok(
                ApiResponse.success(PageResponse.from(result)));
    }

    /**
     * 리뷰 목록 조회
     * @param storeId
     * @param sort
     * @param photoOnly
     * @param page
     * @param size
     * @return
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReviewGetResponseDto>>> getReviews(@RequestParam UUID storeId,
                                                                                      @RequestParam(defaultValue = "LATEST") ReviewSortType sort,
                                                                                      @RequestParam(defaultValue = "false") boolean photoOnly,
                                                                                      @RequestParam(defaultValue = "0") int page,
                                                                                      @RequestParam(defaultValue = "10") int size){
        int safeSize = Math.min(size, 50);   //size ~ 최대 50개 까지
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
                                                                             @RequestHeader("X-Role") String userRole) {
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
