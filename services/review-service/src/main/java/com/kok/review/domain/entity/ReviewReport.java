package com.kok.review.domain.entity;

import com.omakase.kok.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_review_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_report_review_reporter",
                columnNames = {"review_id", "reporter_id"}))  // 한 사람이 같은 리뷰 1번만
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "report_id", updatable = false)
    private UUID reportId;

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;                        //신고자

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private ReportReason reason;

    @Column(name = "detail", length = 500)
    private String detail;          // ETC일 때 상세 사유

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportStatus status;     //신고 상태

    @Builder
    private ReviewReport(UUID reviewId, UUID reporterId, ReportReason reason, String detail) {
        this.reviewId = reviewId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.detail = detail;
        this.status = ReportStatus.PENDING;   // 생성 시 항상 접수 상태
    }

    //신고 생성
    public static ReviewReport create(UUID reviewId, UUID reporterId,
                                      ReportReason reason, String detail) {
        return ReviewReport.builder()
                .reviewId(reviewId)
                .reporterId(reporterId)
                .reason(reason)
                .detail(detail)
                .build();
    }

    /** 관리자 승인 처리 */
    public void resolve() {
        this.status = ReportStatus.RESOLVED;
    }

    /** 관리자 거부 처리 */
    public void reject() {
        this.status = ReportStatus.REJECTED;
    }

    /** 아직 처리 전(PENDING)인지 */
    public boolean isPending() {
        return this.status == ReportStatus.PENDING;
    }
}
