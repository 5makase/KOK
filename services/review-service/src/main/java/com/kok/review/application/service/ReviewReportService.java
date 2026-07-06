package com.kok.review.application.service;

import com.kok.review.domain.entity.ReportReason;
import com.kok.review.domain.entity.Review;
import com.kok.review.domain.entity.ReviewReport;
import com.kok.review.domain.repository.ReviewReportRepository;
import com.kok.review.domain.repository.ReviewRepository;
import com.kok.review.global.exception.ReviewErrorCode;
import com.kok.review.presentation.DTO1.request.ReviewReportRequestDto;
import com.kok.review.presentation.DTO1.response.ReviewReportResponseDto;
import com.omakase.kok.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewReportService {

    private final ReviewReportRepository reviewReportRepository;
    private final ReviewRepository reviewRepository;

    /**
     * 사용자 리뷰 신고
     * @param reviewId
     * @param reporterId 신고자 ID
     * @param userRole
     * @param dto
     * @return
     */
    @Transactional
    public ReviewReportResponseDto report(UUID reviewId, UUID reporterId,
                                          String userRole, ReviewReportRequestDto dto) {
        // 사용자 권한 확인
        if (!"USER".equals(userRole)) {
            throw new BaseException(ReviewErrorCode.NOT_USER_ROLE);
        }

        //리뷰 존재 확인
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BaseException(ReviewErrorCode.REVIEW_NOT_FOUND));

        //신고자가 본인 리뷰를 신고하는 경우는 막음.
        if (review.getUserId().equals(reporterId)) {
            throw new BaseException(ReviewErrorCode.CANNOT_REPORT_OWN_REVIEW);
        }

        //신고자가 이미 신고한 리뷰는 막음. (1차 방어 — 최종 방어는 복합 UNIQUE)
        if (reviewReportRepository.existsByReviewIdAndReporterId(reviewId, reporterId)) {
            throw new BaseException(ReviewErrorCode.DUPLICATE_REPORT);
        }

        //신고 사유가 기타 사유라면, 상세 내용을 반드시 작성해야함.
        if (dto.getReason() == ReportReason.ETC
                && (dto.getDetail() == null || dto.getDetail().isBlank())) {
            throw new BaseException(ReviewErrorCode.REPORT_DETAIL_REQUIRED);
        }

        // 신고 접수 — 동시 요청 레이스 시 복합 UNIQUE 위반을 409로 변환
        ReviewReport report = ReviewReport.create(
                reviewId, reporterId, dto.getReason(), dto.getDetail());
        try {
            reviewReportRepository.save(report);
            reviewReportRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BaseException(ReviewErrorCode.DUPLICATE_REPORT);
        }

        return ReviewReportResponseDto.from(report);
    }
}
