package com.omakase.kok.store.infrastructure.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreRankingRedisRepositoryTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    StoreRankingRedisRepository repository;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    // updateScore

    @Test
    @DisplayName("updateScore - ZADD 호출 (score=평점 double)")
    void updateScore_calls_zadd() {
        UUID storeId = UUID.randomUUID();
        BigDecimal rating = new BigDecimal("4.50");

        repository.updateScore(storeId, rating);

        verify(zSetOperations).add("store:ranking", storeId.toString(), 4.50);
    }

    // remove

    @Test
    @DisplayName("remove - ZREM 호출")
    void remove_calls_zrem() {
        UUID storeId = UUID.randomUUID();

        repository.remove(storeId);

        verify(zSetOperations).remove("store:ranking", storeId.toString());
    }

    // getTopRanking

    @Test
    @DisplayName("getTopRanking - reverseRange 결과를 UUID 리스트로 반환")
    void getTopRanking_returns_uuid_list_in_order() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        // LinkedHashSet으로 순서 보장
        Set<String> result = new LinkedHashSet<>();
        result.add(id1.toString());
        result.add(id2.toString());

        when(zSetOperations.reverseRange("store:ranking", 0, 4L)).thenReturn(result);

        List<UUID> ranking = repository.getTopRanking(5);

        assertThat(ranking).containsExactly(id1, id2);
    }

    @Test
    @DisplayName("getTopRanking - size=0 이면 빈 리스트 반환 (Redis 호출 없음)")
    void getTopRanking_size_zero_returns_empty_without_redis_call() {
        List<UUID> ranking = repository.getTopRanking(0);

        assertThat(ranking).isEmpty();
        verify(redisTemplate, never()).opsForZSet();
    }

    @Test
    @DisplayName("getTopRanking - Redis가 null 반환하면 빈 리스트")
    void getTopRanking_redis_returns_null_gives_empty() {
        when(zSetOperations.reverseRange("store:ranking", 0, 2L)).thenReturn(null);

        List<UUID> ranking = repository.getTopRanking(3);

        assertThat(ranking).isEmpty();
    }

    @Test
    @DisplayName("getTopRanking - size 개수만큼 reverseRange(0, size-1) 호출")
    void getTopRanking_range_end_is_size_minus_one() {
        when(zSetOperations.reverseRange("store:ranking", 0, 9L)).thenReturn(Set.of());

        repository.getTopRanking(10);

        verify(zSetOperations).reverseRange("store:ranking", 0, 9L);
    }
}
