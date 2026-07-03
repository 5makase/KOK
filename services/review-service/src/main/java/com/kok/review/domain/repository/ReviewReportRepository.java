package com.kok.review.domain.repository;

import com.kok.review.domain.entity.ReportStatus;
import com.kok.review.domain.entity.ReviewReport;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.awt.print.Pageable;
import java.util.UUID;

@Repository
public interface ReviewReportRepository extends JpaRepository<ReviewReport, UUID> {

    // 중복 신고 1차 방어 (최종 방어는 복합 UNIQUE)
    boolean existsByReviewIdAndReporterId(UUID reviewId, UUID reporterId);

    // 관리자 신고 목록 조회 (상태별)
    Page<ReviewReport> findByStatus(ReportStatus status, Pageable pageable);

    // 특정 리뷰의 누적 신고 건수 (관리자 참고용)
    long countByReviewId(UUID reviewId);
}
