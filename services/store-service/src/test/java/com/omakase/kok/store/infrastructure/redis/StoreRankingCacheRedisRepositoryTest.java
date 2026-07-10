package com.omakase.kok.store.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.store.application.result.StoreRankingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreRankingCacheRedisRepositoryTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    StoreRankingCacheRedisRepository repository;

    private static final int SIZE = 10;
    private static final String CACHE_KEY = "store:ranking:response:" + SIZE;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // get

    @Test
    @DisplayName("캐시 히트 - JSON 역직렬화 후 Optional로 반환")
    void get_cache_hit_returns_deserialized_result() throws Exception {
        List<StoreRankingResult> expected = List.of(rankingResult(1));
        String json = objectMapper.writeValueAsString(expected);
        when(valueOperations.get(CACHE_KEY)).thenReturn(json);

        Optional<List<StoreRankingResult>> result = repository.get(SIZE);

        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1);
        assertThat(result.get().get(0).getRank()).isEqualTo(1);
    }

    @Test
    @DisplayName("캐시 미스 - Redis null 반환 시 Optional.empty()")
    void get_cache_miss_returns_empty() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);

        assertThat(repository.get(SIZE)).isEmpty();
    }

    @Test
    @DisplayName("역직렬화 실패 시 Optional.empty() - 예외 전파 없음")
    void get_invalid_json_returns_empty_without_throw() {
        when(valueOperations.get(CACHE_KEY)).thenReturn("{invalid-json}");

        assertThatCode(() -> repository.get(SIZE)).doesNotThrowAnyException();
        assertThat(repository.get(SIZE)).isEmpty();
    }

    @Test
    @DisplayName("Redis 조회 예외 시 Optional.empty() - 캐시 미스로 처리 (Redis 전체 장애 시 DB fallback은 TODO)")
    void get_redis_exception_returns_empty() {
        when(valueOperations.get(CACHE_KEY)).thenThrow(new RuntimeException("Redis 연결 실패"));

        assertThatCode(() -> repository.get(SIZE)).doesNotThrowAnyException();
        assertThat(repository.get(SIZE)).isEmpty();
    }

    // set

    @Test
    @DisplayName("set - TTL 300초로 직렬화 후 저장")
    void set_stores_json_with_300s_ttl() {
        List<StoreRankingResult> results = List.of(rankingResult(1));

        repository.set(SIZE, results);

        verify(valueOperations).set(eq(CACHE_KEY), anyString(), eq(Duration.ofSeconds(300)));
    }

    @Test
    @DisplayName("set - Redis 저장 실패 시 예외 전파 없음 (캐싱 생략)")
    void set_redis_failure_does_not_throw() {
        doThrow(new RuntimeException("Redis down"))
                .when(valueOperations).set(anyString(), anyString(), any());

        assertThatCode(() -> repository.set(SIZE, List.of(rankingResult(1))))
                .doesNotThrowAnyException();
    }

    // evictAll

    @Test
    @DisplayName("evictAll - SCAN으로 store:ranking:response:* 키 수집 후 일괄 삭제")
    void evictAll_scans_and_deletes_matching_keys() {
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn("store:ranking:response:10", "store:ranking:response:20");
        doCallRealMethod().when(cursor).forEachRemaining(any());
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);

        repository.evictAll();

        verify(redisTemplate).delete(anyCollection());
    }

    @Test
    @DisplayName("evictAll - 매칭 키 없으면 delete 호출 안 함")
    void evictAll_no_matching_keys_skips_delete() {
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(false);
        doCallRealMethod().when(cursor).forEachRemaining(any());
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);

        repository.evictAll();

        verify(redisTemplate, never()).delete(anyCollection());
    }

    @Test
    @DisplayName("evictAll - Redis 예외 발생해도 전파 없음 (TTL 만료 대기)")
    void evictAll_redis_exception_does_not_throw() {
        when(redisTemplate.scan(any(ScanOptions.class))).thenThrow(new RuntimeException("Redis down"));

        assertThatCode(() -> repository.evictAll()).doesNotThrowAnyException();
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
