package com.omakase.kok.store.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.store.application.cache.StoreListCacheRepository;
import com.omakase.kok.store.application.result.StoreResult;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
public class StoreListRedisRepository implements StoreListCacheRepository {

    private static final String LIST_KEY_PREFIX = "store:list:";
    private static final Duration TTL = Duration.ofSeconds(300);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<Page<StoreResult>> get(String cacheKey, Pageable pageable) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json == null) return Optional.empty();
            CachedPage cached = objectMapper.readValue(json, CachedPage.class);
            return Optional.of(cached.toPage(pageable));
        } catch (JsonProcessingException e) {
            log.warn("매장 목록 캐시 역직렬화 실패 — 캐시 미스로 처리. key={}", cacheKey, e);
            return Optional.empty();
        }
    }

    @Override
    public void set(String cacheKey, Page<StoreResult> page) {
        try {
            String json = objectMapper.writeValueAsString(CachedPage.from(page));
            redisTemplate.opsForValue().set(cacheKey, json, TTL);
        } catch (JsonProcessingException e) {
            log.warn("매장 목록 캐시 직렬화 실패 — 캐싱 생략. key={}", cacheKey, e);
        }
    }

    @Override
    public void evictAll() {
        // SCAN - 운영 환경에서 블로킹 방지
        try {
            List<String> keys = new ArrayList<>();
            try (var cursor = redisTemplate.scan(
                    ScanOptions.scanOptions().match(LIST_KEY_PREFIX + "*").count(100).build())) {
                cursor.forEachRemaining(keys::add);
            }
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("store:list:* 캐시 무효화. 삭제 키 수={}", keys.size());
            }
        } catch (Exception e) {
            log.warn("store:list:* 캐시 무효화 실패 - TTL 만료 후 자동 제거됨", e);
        }
    }

    // Jackson 직렬화/역직렬화용 내부 DTO - PageImpl은 역직렬화 불가
    @Getter
    @NoArgsConstructor
    private static class CachedPage {
        private List<StoreResult> content;
        private long totalElements;

        private CachedPage(List<StoreResult> content, long totalElements) {
            this.content = content;
            this.totalElements = totalElements;
        }

        static CachedPage from(Page<StoreResult> page) {
            return new CachedPage(page.getContent(), page.getTotalElements());
        }

        Page<StoreResult> toPage(Pageable pageable) {
            return new PageImpl<>(content, pageable, totalElements);
        }
    }
}
