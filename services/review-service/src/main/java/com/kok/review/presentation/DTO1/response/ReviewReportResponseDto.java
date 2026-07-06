package com.kok.review.presentation.DTO1.response;

import com.kok.review.domain.entity.ReportReason;
import com.kok.review.domain.entity.ReviewReport;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ReviewReportResponseDto {
    private UUID reportId;
    private UUID reviewId;
    private ReportReason reason;
    private LocalDateTime createdAt;

    public static ReviewReportResponseDto from(ReviewReport report) {
        return ReviewReportResponseDto.builder()
                .reportId(report.getReportId())
                .reviewId(report.getReviewId())
                .reason(report.getReason())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
