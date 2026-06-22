package com.omakase.kok.store.domain.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface StoreRankingRepository {
    void updateScore(UUID storeId, BigDecimal averageRating);
    void remove(UUID storeId);
}
