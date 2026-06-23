package com.omakase.kok.store.domain.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface StoreRankingRepository {
    void updateScore(UUID storeId, BigDecimal averageRating);
    void remove(UUID storeId);
    // averageRating 내림차순 상위 size개 storeId 반환
    List<UUID> getTopRanking(int size);
}
