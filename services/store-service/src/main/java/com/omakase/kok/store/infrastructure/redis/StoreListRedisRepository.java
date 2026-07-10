package com.omakase.kok.store.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.store.application.cache.StoreListCacheRepository;
import com.omakase.kok.store.application.result.StoreResult;
import com.omakase.kok.store.domain.enums.AmenityType;
import com.omakase.kok.store.domain.repository.StoreSearchCondition;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class StoreListRedisRepository implements StoreListCacheRepository {

    private static final String LIST_KEY_PREFIX = "store:list:";
    private static final Duration TTL = Duration.ofSeconds(300);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<Page<StoreResult>> get(StoreSearchCondition condition, Pageable pageable) {
        String cacheKey = buildKey(condition, pageable);
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json == null) return Optional.empty();
            CachedPage cached = objectMapper.readValue(json, CachedPage.class);
            return Optional.of(cached.toPage(pageable));
        } catch (JsonProcessingException e) {
            log.warn("매장 목록 캐시 역직렬화 실패 - 캐시 미스로 처리. key={}", cacheKey, e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("매장 목록 캐시 조회 실패 - DB 조회로 fallback. key={}", cacheKey, e);
            return Optional.empty();
        }
    }

    @Override
    public void set(StoreSearchCondition condition, Pageable pageable, Page<StoreResult> page) {
        String cacheKey = buildKey(condition, pageable);
        try {
            String json = objectMapper.writeValueAsString(CachedPage.from(page));
            redisTemplate.opsForValue().set(cacheKey, json, TTL);
        } catch (JsonProcessingException e) {
            log.warn("매장 목록 캐시 직렬화 실패 - 캐싱 생략. key={}", cacheKey, e);
        } catch (Exception e) {
            log.warn("매장 목록 캐시 저장 실패 - 캐싱 생략. key={}", cacheKey, e);
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

    // 키 형식: store:list:{category}:{sido}:{sigungu}:{keyword}:{amenities}:{status}:{sort}:{page}:{size}
    // 조건 미지정 필드는 "ALL"로 대체해 키 충돌 방지
    // ownerId는 키에 포함하지 않음: OWNER 요청은 캐시 효과가 낮고 다른 OWNER 데이터와 격리 필요, StoreService에서 바이패스
    // categoryIds, amenities는 요청 순서와 무관하게 동일 캐시 키를 보장하기 위해 정렬 후 결합
    private String buildKey(StoreSearchCondition condition, Pageable pageable) {
        String categoryPart = (condition.getCategoryIds() == null || condition.getCategoryIds().isEmpty())
                ? "ALL"
                : condition.getCategoryIds().stream()
                        .map(UUID::toString)
                        .sorted()
                        .collect(Collectors.joining("_"));
        String amenityPart = (condition.getAmenities() == null || condition.getAmenities().isEmpty())
                ? "ALL"
                : condition.getAmenities().stream()
                        .map(AmenityType::name)
                        .sorted()
                        .reduce((a, b) -> a + "_" + b)
                        .orElse("ALL");
        return LIST_KEY_PREFIX +
                categoryPart + ":" +
                orAll(condition.getSido()) + ":" +
                orAll(condition.getSigungu()) + ":" +
                orAll(condition.getKeyword()) + ":" +
                amenityPart + ":" +
                orAll(condition.getStatus()) + ":" +
                orAll(condition.getSort()) + ":" +
                pageable.getPageNumber() + ":" +
                pageable.getPageSize();
    }

    private String orAll(Object value) {
        return value != null ? value.toString() : "ALL";
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
