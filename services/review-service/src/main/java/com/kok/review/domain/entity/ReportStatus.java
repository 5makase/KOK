package com.kok.review.domain.entity;

public enum ReportStatus {
    PENDING,    // 접수 (처리 대기)
    RESOLVED,   // 승인 처리 완료 (블라인드 확정)
    REJECTED    // 거부 (정상 리뷰로 판단)
}