package com.omakase.kok.waiting.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class WaitingQueueRedisStore {
    private final StringRedisTemplate redisTemplate;

    private static final String WAITING_QUEUE_KEY_PREFIX = "waiting:store:";
    private String key(UUID storeId) {
        return WAITING_QUEUE_KEY_PREFIX + storeId;
    }

    // 대기열 등록
    public void add(UUID storeId, UUID waitingId, Long waitingNumber) {
        redisTemplate.opsForZSet().add(key(storeId), waitingId.toString(), waitingNumber);
    }

    // 현재 순번 조회
    public Long getRank(UUID storeId, UUID waitingId) {
        Long rank = redisTemplate.opsForZSet().rank(key(storeId), waitingId.toString());
        if (rank == null) {
            return null;
        }
        return rank + 1;
    }

    // 대기열 제거
    public void remove(UUID storeId, UUID waitingId) {
        redisTemplate.opsForZSet().remove(key(storeId), waitingId.toString());
    }

    // 다음 호출 대상 조회
    public Optional<UUID> findFirst(UUID storeId) {
        Set<String> waitingIds = redisTemplate.opsForZSet().range(key(storeId), 0, 0);
        if (waitingIds == null || waitingIds.isEmpty()) {
            return Optional.empty();
        }
        return waitingIds.stream()
                .findFirst()
                .map(UUID::fromString);
    }

    // 순번 임박 대상 조회
    public List<UUID> findNearTurn(UUID storeId, int threshold) {
        if (threshold <= 0) {
            return List.of();
        }
        Set<String> waitingIds = redisTemplate.opsForZSet().range(key(storeId), 0, threshold - 1L);
        if (waitingIds == null || waitingIds.isEmpty()) {
            return List.of();
        }
        return waitingIds.stream()
                .map(UUID::fromString)
                .toList();
    }

    // 현재 대기 팀 수 조회
    public Long count(UUID storeId) {
        Long count = redisTemplate.opsForZSet().zCard(key(storeId));
        if (count == null) {
            return 0L;
        }
        return count;
    }
}
