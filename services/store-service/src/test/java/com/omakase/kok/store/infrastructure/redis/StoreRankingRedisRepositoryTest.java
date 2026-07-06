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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreRankingRedisRepositoryTest {

    private static final String REBUILD_LOCK_KEY = "store:ranking:rebuild:lock";

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ZSetOperations<String, String> zSetOperations;
    @Mock RedissonClient redissonClient;
    @Mock RLock rLock;

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

    // tryAcquireRebuildLock

    @Test
    @DisplayName("tryAcquireRebuildLock - 락 획득 성공 시 true")
    void tryAcquireRebuildLock_returns_true_when_lock_acquired() throws InterruptedException {
        when(redissonClient.getLock(REBUILD_LOCK_KEY)).thenReturn(rLock);
        when(rLock.tryLock(0, 3, TimeUnit.SECONDS)).thenReturn(true);

        assertThat(repository.tryAcquireRebuildLock()).isTrue();
    }

    @Test
    @DisplayName("tryAcquireRebuildLock - 이미 다른 요청이 잡고 있으면 false")
    void tryAcquireRebuildLock_returns_false_when_already_held() throws InterruptedException {
        when(redissonClient.getLock(REBUILD_LOCK_KEY)).thenReturn(rLock);
        when(rLock.tryLock(0, 3, TimeUnit.SECONDS)).thenReturn(false);

        assertThat(repository.tryAcquireRebuildLock()).isFalse();
    }

    @Test
    @DisplayName("tryAcquireRebuildLock - Redis 호출 자체가 예외를 던져도 false로 방어 (재구성만 생략되도록)")
    void tryAcquireRebuildLock_returns_false_when_redis_throws() {
        when(redissonClient.getLock(REBUILD_LOCK_KEY)).thenThrow(new RuntimeException("Redis 연결 실패"));

        assertThat(repository.tryAcquireRebuildLock()).isFalse();
    }

    @Test
    @DisplayName("tryAcquireRebuildLock - 인터럽트 발생 시에도 false로 방어하고 인터럽트 상태를 복원")
    void tryAcquireRebuildLock_returns_false_when_interrupted() throws InterruptedException {
        when(redissonClient.getLock(REBUILD_LOCK_KEY)).thenReturn(rLock);
        when(rLock.tryLock(0, 3, TimeUnit.SECONDS)).thenThrow(new InterruptedException());

        assertThat(repository.tryAcquireRebuildLock()).isFalse();
        assertThat(Thread.interrupted()).isTrue(); // 확인과 동시에 플래그를 지우므로 테스트 뒷정리도 겸함
    }

    // rebuildAll

    @Test
    @DisplayName("rebuildAll - storeId/평점 전체를 ZADD로 재적재")
    void rebuildAll_calls_zadd_with_all_scores() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Map<UUID, BigDecimal> scores = Map.of(id1, new BigDecimal("4.50"), id2, new BigDecimal("3.20"));

        repository.rebuildAll(scores);

        verify(zSetOperations).add(eq("store:ranking"), argThatContainsBothTuples(id1, id2));
    }

    @Test
    @DisplayName("rebuildAll - ZADD 도중 Redis가 예외를 던져도 삼키고 정상 반환 (호출부는 DB 응답을 그대로 써야 함)")
    void rebuildAll_swallows_exception_when_redis_throws() {
        UUID id1 = UUID.randomUUID();
        when(zSetOperations.add(anyString(), any(Set.class))).thenThrow(new RuntimeException("Redis 연결 실패"));

        repository.rebuildAll(Map.of(id1, new BigDecimal("4.50")));
        // 예외 없이 메서드가 정상 종료되면 성공
    }

    @Test
    @DisplayName("rebuildAll - 빈 맵이면 ZADD 호출 없이 반환")
    void rebuildAll_does_nothing_when_map_empty() {
        repository.rebuildAll(Map.of());

        verify(zSetOperations, never()).add(anyString(), any(Set.class));
    }

    @SuppressWarnings("unchecked")
    private Set<ZSetOperations.TypedTuple<String>> argThatContainsBothTuples(UUID id1, UUID id2) {
        return org.mockito.ArgumentMatchers.argThat(tuples ->
                tuples != null && tuples.size() == 2
                        && tuples.stream().anyMatch(t -> id1.toString().equals(t.getValue()))
                        && tuples.stream().anyMatch(t -> id2.toString().equals(t.getValue())));
    }
}
