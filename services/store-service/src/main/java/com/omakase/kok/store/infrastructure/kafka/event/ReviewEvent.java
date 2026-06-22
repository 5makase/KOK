package com.omakase.kok.store.infrastructure.kafka.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class ReviewEvent {

    private UUID eventId;
    private String eventType;
    private Integer schemaVersion;
    private LocalDateTime occurredAt;
    private String producer;
    private Payload payload;

    @Getter
    @NoArgsConstructor
    public static class Payload {
        private UUID reviewId;
        private UUID storeId;
        private BigDecimal rating;           // 이번 리뷰 개별 평점
        private BigDecimal averageRating;    // 삭제/수정 반영 후 매장 전체 평균 평점
        private Integer reviewCount;         // 삭제/수정 반영 후 매장 전체 리뷰 수
    }
}
