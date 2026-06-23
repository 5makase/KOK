package com.omakase.kok.store.presentation.dto.response;

import com.omakase.kok.store.application.result.StoreRankingResult;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class StoreRankingResponse {

    private List<RankingItem> rankings;

    @Getter
    @Builder
    public static class RankingItem {

        private int rank;
        private UUID storeId;
        private String name;
        private String categoryName;
        private String status;
        private BigDecimal averageRating;
        private int reviewCount;
        private String thumbnailUrl;

        public static RankingItem from(StoreRankingResult result) {
            return RankingItem.builder()
                    .rank(result.getRank())
                    .storeId(result.getStoreId())
                    .name(result.getName())
                    .categoryName(result.getCategoryName())
                    .status(result.getStatus())
                    .averageRating(result.getAverageRating())
                    .reviewCount(result.getReviewCount())
                    .thumbnailUrl(result.getThumbnailUrl())
                    .build();
        }
    }

    public static StoreRankingResponse from(List<StoreRankingResult> results) {
        return StoreRankingResponse.builder()
                .rankings(results.stream().map(RankingItem::from).toList())
                .build();
    }
}
