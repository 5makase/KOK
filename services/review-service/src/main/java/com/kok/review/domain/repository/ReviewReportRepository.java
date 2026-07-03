package com.kok.review.domain.repository;

import com.kok.review.domain.entity.ReportStatus;
import com.kok.review.domain.entity.ReviewReport;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import java.util.UUID;

@Repository
public interface ReviewReportRepository extends JpaRepository<ReviewReport, UUID> {

    // 중복 신고 1차 방어 (최종 방어는 복합 UNIQUE)
    boolean existsByReviewIdAndReporterId(UUID reviewId, UUID reporterId);

    // 관리자 신고 목록 조회 (상태별)
    Page<ReviewReport> findByStatus(ReportStatus status, Pageable pageable);

    // 특정 리뷰의 누적 신고 건수 (관리자 참고용)
    long countByReviewId(UUID reviewId);

    /*아래 UPDATE 쿼리문을 JPA 1차 캐시를 우회하여 DB에 직접 적용.
    *   - @Modifying은 JPA에게 UPDATE 쿼리문을 날리겠다는 신호.*/
    @Modifying(clearAutomatically = true)
    @Query("update ReviewReport r set r.status = :next " +
            "where r.reportId = :reportId and r.status = com.kok.review.domain.entity.ReportStatus.PENDING")
    /*UPDATE 쿼리문은 몇 줄을 변경했는지 그 줄의 수를 반환함.
    * - 여기서는 0 또은 1임.*/
    int transitionIfPending(@Param("reportId") UUID reportId,
                            @Param("next") ReportStatus next);
}
