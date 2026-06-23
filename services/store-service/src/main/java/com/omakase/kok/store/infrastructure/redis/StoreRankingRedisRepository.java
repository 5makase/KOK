package com.omakase.kok.store.infrastructure.redis;

import com.omakase.kok.store.domain.repository.StoreRankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class StoreRankingRedisRepository implements StoreRankingRepository {

    private static final String RANKING_KEY = "store:ranking";

    private final RedisTemplate<String, String> redisTemplate;

    // ZADD store:ranking {score} {storeId} - score 중복 시 덮어씀 (멱등)
    public void updateScore(UUID storeId, BigDecimal averageRating) {
        redisTemplate.opsForZSet().add(RANKING_KEY, storeId.toString(), averageRating.doubleValue());
    }

    // reviewCount == 0 (마지막 리뷰 삭제) 시 랭킹에서 제거
    public void remove(UUID storeId) {
        redisTemplate.opsForZSet().remove(RANKING_KEY, storeId.toString());
    }

    // averageRating 내림차순(reverseRange) 상위 size개 storeId 반환
    public List<UUID> getTopRanking(int size) {
        if (size <= 0) return Collections.emptyList();
        Set<String> result = redisTemplate.opsForZSet().reverseRange(RANKING_KEY, 0, size - 1L);
        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }
        return result.stream().map(UUID::fromString).toList();
    }
}
