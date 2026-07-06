package com.omakase.kok.store.infrastructure.redis;

import com.omakase.kok.store.application.result.StoreRankingResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StoreRankingCacheRedisRepository 통합 테스트 - Redis 사용
 * 실행 전제: docker compose up -d redis (application-test.yml: localhost:6379)
 */
@SpringBootTest
@ActiveProfiles("test")
class StoreRankingCacheRedisRepositoryIntegrationTest {

    @Autowired StoreRankingCacheRedisRepository repository;
    @Autowired RedisTemplate<String, String> redisTemplate;

    // 이전 실행이 비정상 종료되거나 수동 테스트로 store:ranking:response:*에 데이터가 남았을 때
    // 첫 테스트가 그 잔여 데이터 때문에 실패할 수 있어, 시작 전에도 동일하게 정리한다
    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void cleanUp() {
        Set<String> keys = redisTemplate.keys("store:ranking:response:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
    }

    @Test
    @DisplayName("set 후 get - 동일 size로 캐시 히트")
    void set_and_get_cache_hit() {
        List<StoreRankingResult> original = List.of(rankingResult(1), rankingResult(2));

        repository.set(10, original);
        Optional<List<StoreRankingResult>> cached = repository.get(10);

        assertThat(cached).isPresent();
        assertThat(cached.get()).hasSize(2);
        assertThat(cached.get().get(0).getRank()).isEqualTo(1);
        assertThat(cached.get().get(1).getRank()).isEqualTo(2);
    }

    @Test
    @DisplayName("size가 다르면 캐시 미스")
    void get_different_size_cache_miss() {
        repository.set(10, List.of(rankingResult(1)));

        assertThat(repository.get(20)).isEmpty();
    }

    @Test
    @DisplayName("캐시 저장 안 된 상태에서 get - Optional.empty()")
    void get_before_set_returns_empty() {
        assertThat(repository.get(10)).isEmpty();
    }

    @Test
    @DisplayName("evictAll - store:ranking:response:* 키 전체 삭제")
    void evictAll_removes_all_ranking_cache_keys() {
        repository.set(10, List.of(rankingResult(1)));
        repository.set(20, List.of(rankingResult(1)));

        repository.evictAll();

        assertThat(repository.get(10)).isEmpty();
        assertThat(repository.get(20)).isEmpty();
    }

    @Test
    @DisplayName("TTL 설정 확인 - 키에 만료 시간이 설정됨")
    void set_applies_ttl() {
        repository.set(10, List.of(rankingResult(1)));

        Set<String> keys = redisTemplate.keys("store:ranking:response:*");
        assertThat(keys).isNotEmpty();
        Long ttl = redisTemplate.getExpire(keys.iterator().next());
        assertThat(ttl).isGreaterThan(0);
    }

    @Test
    @DisplayName("set 후 evictAll - 재조회 시 캐시 미스")
    void set_then_evict_then_get_returns_empty() {
        repository.set(10, List.of(rankingResult(1)));
        assertThat(repository.get(10)).isPresent();

        repository.evictAll();

        assertThat(repository.get(10)).isEmpty();
    }

    // helpers

    private StoreRankingResult rankingResult(int rank) {
        return StoreRankingResult.builder()
                .rank(rank)
                .storeId(UUID.randomUUID())
                .name("테스트 매장 " + rank)
                .categoryName("한식")
                .status("OPEN")
                .averageRating(new BigDecimal("4.50"))
                .reviewCount(10)
                .build();
    }
}
