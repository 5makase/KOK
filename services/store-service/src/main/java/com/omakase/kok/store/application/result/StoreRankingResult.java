package com.omakase.kok.store.application.result;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.enums.StoreStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class StoreRankingResult {

    private int rank;
    private UUID storeId;
    private String name;
    private String categoryName;
    private StoreStatus status;
    private BigDecimal averageRating;
    private int reviewCount;
    private String thumbnailUrl;

    public static StoreRankingResult of(int rank, Store store) {
        return StoreRankingResult.builder()
                .rank(rank)
                .storeId(store.getStoreId())
                .name(store.getName())
                .categoryName(store.getCategory().getName())
                .status(store.getStatus())
                .averageRating(store.getAverageRating())
                .reviewCount(store.getReviewCount())
                .thumbnailUrl(store.getThumbnailUrl())
                .build();
    }
}
