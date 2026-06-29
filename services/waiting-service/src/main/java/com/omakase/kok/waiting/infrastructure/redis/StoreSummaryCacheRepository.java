package com.omakase.kok.waiting.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.waiting.infrastructure.client.dto.StoreSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class StoreSummaryCacheRepository {
    private static final String KEY_PREFIX = "waiting:store-summary:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<StoreSummaryResponse> get(UUID storeId) {
        String cacheKey = cacheKey(storeId);
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, StoreSummaryResponse.class));
        } catch (JsonProcessingException e) {
            log.warn("매장 요약 캐시 역직렬화 실패 - 캐시 미스로 처리. key={}", cacheKey, e);
            evictMalformedCache(cacheKey);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("매장 요약 캐시 조회 실패 - store-service 조회로 fallback. key={}", cacheKey, e);
            return Optional.empty();
        }
    }

    public void set(UUID storeId, StoreSummaryResponse storeSummary) {
        String cacheKey = cacheKey(storeId);
        try {
            String json = objectMapper.writeValueAsString(storeSummary);
            redisTemplate.opsForValue().set(cacheKey, json, TTL);
        } catch (JsonProcessingException e) {
            log.warn("매장 요약 캐시 직렬화 실패 - 캐싱 생략. key={}", cacheKey, e);
        } catch (Exception e) {
            log.warn("매장 요약 캐시 저장 실패 - 캐싱 생략. key={}", cacheKey, e);
        }
    }

    private String cacheKey(UUID storeId) {
        return KEY_PREFIX + storeId;
    }

    private void evictMalformedCache(String cacheKey) {
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.warn("역직렬화 실패한 매장 요약 캐시 삭제 실패. key={}", cacheKey, e);
        }
    }
}
