package com.omakase.kok.store.application.result;

import com.omakase.kok.store.domain.entity.Store;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreRankingResult {

    private int rank;
    private UUID storeId;
    private String name;
    private String categoryName;
    private String status; // enum → String 변환 (Presentation 계층에 도메인 타입 노출 방지)
    private BigDecimal averageRating;
    private int reviewCount;
    private String thumbnailUrl;

    public static StoreRankingResult of(int rank, Store store) {
        return StoreRankingResult.builder()
                .rank(rank)
                .storeId(store.getStoreId())
                .name(store.getName())
                .categoryName(store.getCategory().getName())
                .status(store.getStatus().name())
                .averageRating(store.getAverageRating())
                .reviewCount(store.getReviewCount())
                .thumbnailUrl(store.getThumbnailUrl())
                .build();
    }
}
