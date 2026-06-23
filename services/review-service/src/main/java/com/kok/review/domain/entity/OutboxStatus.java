package com.kok.review.domain.entity;

public enum OutboxStatus {
    PENDING,    // 발행 대기
    PUBLISHED,  // 발행 성공
    FAILED      // 발행 실패
}
