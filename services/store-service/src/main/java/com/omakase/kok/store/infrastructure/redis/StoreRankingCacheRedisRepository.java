package com.omakase.kok.store.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.store.application.cache.StoreRankingCacheRepository;
import com.omakase.kok.store.application.result.StoreRankingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class StoreRankingCacheRedisRepository implements StoreRankingCacheRepository {

    private static final String RESPONSE_KEY_PREFIX = "store:ranking:response:";
    private static final Duration TTL = Duration.ofSeconds(300);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<List<StoreRankingResult>> get(int size) {
        String cacheKey = buildKey(size);
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json == null) {
                log.debug("store:ranking 캐시 미스. key={}", cacheKey);
                return Optional.empty();
            }

            List<StoreRankingResult> results = objectMapper.readValue(json, new TypeReference<>() {});
            log.debug("store:ranking 캐시 히트. key={}", cacheKey);

            return Optional.of(results);
        } catch (JsonProcessingException e) {
            log.warn("랭킹 응답 캐시 역직렬화 실패 - 캐시 미스로 처리. key={}", cacheKey, e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("랭킹 응답 캐시 조회 실패 - DB 조회로 fallback. key={}", cacheKey, e);
            return Optional.empty();
        }
    }

    @Override
    public void set(int size, List<StoreRankingResult> results) {
        String cacheKey = buildKey(size);
        try {
            String json = objectMapper.writeValueAsString(results);
            redisTemplate.opsForValue().set(cacheKey, json, TTL);
        } catch (JsonProcessingException e) {
            log.warn("랭킹 응답 캐시 직렬화 실패 - 캐싱 생략. key={}", cacheKey, e);
        } catch (Exception e) {
            log.warn("랭킹 응답 캐시 저장 실패 - 캐싱 생략. key={}", cacheKey, e);
        }
    }

    @Override
    public void evictAll() {
        // SCAN - KEYS 블로킹 회피. size별 키가 적어 count(100)으로 충분
        try {
            List<String> keys = new ArrayList<>();
            try (var cursor = redisTemplate.scan(
                    ScanOptions.scanOptions().match(RESPONSE_KEY_PREFIX + "*").count(100).build())) {
                cursor.forEachRemaining(keys::add);
            }

            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("store:ranking:response:* 캐시 무효화. 삭제 키 수={}", keys.size());
            }
        } catch (Exception e) {
            // 무효화 실패 시 TTL(5분) 만료까지 stale 데이터 허용 - 평점 변경이 즉시 반영되지 않는 트레이드오프
            log.warn("store:ranking:response:* 캐시 무효화 실패 - TTL 만료 후 자동 제거됨", e);
        }
    }

    private String buildKey(int size) {
        return RESPONSE_KEY_PREFIX + size;
    }
}
