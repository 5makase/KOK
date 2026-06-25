package com.omakase.kok.store.infrastructure.redis;

import com.omakase.kok.store.application.result.StoreResult;
import com.omakase.kok.store.domain.repository.StoreSearchCondition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StoreListRedisRepository 통합 테스트
 * 실행 전제: docker compose up -d redis (application-test.yml: localhost:6379)
 */
@SpringBootTest
@ActiveProfiles("test")
class StoreListRedisRepositoryIntegrationTest {

    @Autowired StoreListRedisRepository repository;
    @Autowired RedisTemplate<String, String> redisTemplate;

    private StoreSearchCondition condition;
    private PageRequest pageable;

    @BeforeEach
    void setUp() {
        condition = StoreSearchCondition.builder().sido("서울").sigungu("강남구").build();
        pageable = PageRequest.of(0, 20);
    }

    @AfterEach
    void cleanUp() {
        Set<String> keys = redisTemplate.keys("store:list:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
    }

    @Test
    @DisplayName("set 후 get - 동일 조건으로 캐시 히트")
    void set_and_get_cache_hit() {
        Page<StoreResult> original = new PageImpl<>(List.of(), pageable, 42L);

        repository.set(condition, pageable, original);
        Optional<Page<StoreResult>> cached = repository.get(condition, pageable);

        assertThat(cached).isPresent();
        assertThat(cached.get().getTotalElements()).isEqualTo(42L);
    }

    @Test
    @DisplayName("조건이 다르면 캐시 미스")
    void get_different_condition_cache_miss() {
        Page<StoreResult> page = new PageImpl<>(List.of(), pageable, 10L);
        repository.set(condition, pageable, page);

        StoreSearchCondition different = StoreSearchCondition.builder().sido("부산").build();
        Optional<Page<StoreResult>> result = repository.get(different, pageable);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("evictAll - store:list:* 키 전체 삭제")
    void evictAll_removes_all_list_keys() {
        StoreSearchCondition c1 = StoreSearchCondition.builder().sido("서울").build();
        StoreSearchCondition c2 = StoreSearchCondition.builder().sido("부산").build();
        Page<StoreResult> page = new PageImpl<>(List.of(), pageable, 0L);

        repository.set(c1, pageable, page);
        repository.set(c2, pageable, page);

        repository.evictAll();

        assertThat(repository.get(c1, pageable)).isEmpty();
        assertThat(repository.get(c2, pageable)).isEmpty();
    }

    @Test
    @DisplayName("TTL 설정 확인 - 키에 만료 시간이 설정됨")
    void set_applies_ttl() {
        Page<StoreResult> page = new PageImpl<>(List.of(), pageable, 0L);

        repository.set(condition, pageable, page);

        Set<String> keys = redisTemplate.keys("store:list:*");
        assertThat(keys).isNotEmpty();
        Long ttl = redisTemplate.getExpire(keys.iterator().next());
        assertThat(ttl).isGreaterThan(0);
    }
}
