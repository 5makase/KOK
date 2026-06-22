package com.omakase.kok.store.infrastructure.redis;

import com.omakase.kok.store.domain.repository.StoreRankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class StoreRankingRedisRepository implements StoreRankingRepository {

    private static final String RANKING_KEY = "store:ranking";

    private final RedisTemplate<String, String> redisTemplate;

    public void updateScore(UUID storeId, BigDecimal averageRating) {
        redisTemplate.opsForZSet().add(RANKING_KEY, storeId.toString(), averageRating.doubleValue());
    }

    public void remove(UUID storeId) {
        redisTemplate.opsForZSet().remove(RANKING_KEY, storeId.toString());
    }
}
