package com.kok.review.application.service;

import com.kok.review.domain.entity.Review;
import com.kok.review.domain.entity.ReviewReport;
import com.kok.review.domain.repository.ReviewReportRepository;
import com.kok.review.domain.repository.ReviewRepository;
import com.kok.review.global.exception.ReviewErrorCode;
import com.kok.review.infrastructure.messaging.dto.ReviewReportResultPayload;
import com.omakase.kok.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewReportAdminService {

    private final ReviewReportRepository reviewReportRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewOutboxAppender reviewOutboxAppender;

    /**
     * 신고 승인 → 리뷰 블라인드 + 신고자에게 결과 알림
     * @param reportId
     * @param userRole
     */
    @Transactional
    public void approve(UUID reportId, String userRole) {
        ReviewReport report = getPendingReport(reportId, userRole);

        Review review = reviewRepository.findById(report.getReviewId())
                .orElseThrow(() -> new BaseException(ReviewErrorCode.REVIEW_NOT_FOUND));
        // 리뷰를 블라인드 처리
        review.blind();
        // 신고 승인 처리
        report.resolve();

        publishResult(report.getReviewId(), report.getReporterId(),
                review.getStoreId(), "APPROVED");

        log.info("신고 승인 처리. reportId={}, reviewId={}", reportId, report.getReviewId());
    }

    /**
     * 신고 거부 → 리뷰 유지 + 신고자에게 결과 알림
     * @param reportId
     * @param userRole
     */
    @Transactional
    public void reject(UUID reportId, String userRole) {
        ReviewReport report = getPendingReport(reportId, userRole);

        // storeId 확보용 조회
        Review review = reviewRepository.findById(report.getReviewId())
                .orElseThrow(() -> new BaseException(ReviewErrorCode.REVIEW_NOT_FOUND));
        report.reject();

        publishResult(report.getReviewId(), report.getReporterId(),
                review.getStoreId(), "REJECTED");

        log.info("신고 거부 처리. reportId={}, reviewId={}", reportId, report.getReviewId());
    }

    /**
     * 관리자 권한 + PENDING 상태 공통 검증
     * @param reportId
     * @param userRole
     * @return
     */
    private ReviewReport getPendingReport(UUID reportId, String userRole) {
        //관리자 권한이어야함.
        if (!"MASTER".equals(userRole)) {
            throw new BaseException(ReviewErrorCode.NOT_ADMIN_ROLE);
        }

        // 리뷰 신고를 조회 해서
        ReviewReport report = reviewReportRepository.findById(reportId)
                .orElseThrow(() -> new BaseException(ReviewErrorCode.REPORT_NOT_FOUND));

        // 리뷰 신고가 접수된 상태여야 한다.
        if (!report.isPending()) {
            throw new BaseException(ReviewErrorCode.REPORT_ALREADY_PROCESSED);
        }

        return report;
    }

    /**
     * REVIEW_REPORT_RESULT 발행
     * @param reviewId
     * @param reporterId 신고자 ID
     * @param storeId
     * @param result APPROVE or REJECT
     */
    private void publishResult(UUID reviewId, UUID reporterId, UUID storeId, String result) {
        ReviewReportResultPayload payload = ReviewReportResultPayload.of(
                reviewId, reporterId, result, LocalDateTime.now());
        reviewOutboxAppender.append(reviewId, "REVIEW_REPORT_RESULT", storeId, payload);
    }
}