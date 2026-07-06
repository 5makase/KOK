package com.kok.review.domain.entity;

public enum ReportReason {
    SPAM,            // 스팸/홍보
    ABUSE,           // 욕설/비방
    ADVERTISEMENT,   // 광고
    PRIVACY,         // 개인정보 노출
    INAPPROPRIATE,   // 부적절한 내용
    IRRELEVANT,      // 리뷰와 무관한 내용
    ETC              // 기타 (detail 필수)
}
