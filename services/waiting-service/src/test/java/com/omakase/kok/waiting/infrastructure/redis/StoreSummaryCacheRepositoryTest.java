package com.omakase.kok.waiting.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.waiting.infrastructure.client.dto.StoreSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StoreSummaryCacheRepositoryTest {
    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StoreSummaryCacheRepository repository;

    @BeforeEach
    void setUp() {
        repository = new StoreSummaryCacheRepository(redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("캐시 값이 깨진 JSON이면 키를 삭제하고 캐시 미스로 처리한다")
    void get_malformedJsonDeletesCacheKey() {
        UUID storeId = UUID.randomUUID();
        String cacheKey = "waiting:store-summary:" + storeId;
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(cacheKey)).willReturn("invalid json");

        Optional<StoreSummaryResponse> result = repository.get(storeId);

        assertThat(result).isEmpty();
        verify(redisTemplate).delete(cacheKey);
    }

    @Test
    @DisplayName("캐시 값이 정상 JSON이면 데이터를 반환하고 키를 삭제하지 않는다")
    void get_validJsonReturnsSummary() throws Exception {
        UUID storeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String cacheKey = "waiting:store-summary:" + storeId;
        StoreSummaryResponse summary = StoreSummaryResponse.of(storeId, "테스트 매장", ownerId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(cacheKey)).willReturn(objectMapper.writeValueAsString(summary));

        Optional<StoreSummaryResponse> result = repository.get(storeId);

        assertThat(result).isPresent();
        assertThat(result.get().getStoreId()).isEqualTo(storeId);
        assertThat(result.get().getStoreName()).isEqualTo("테스트 매장");
        assertThat(result.get().getOwnerId()).isEqualTo(ownerId);
        verify(redisTemplate, never()).delete(cacheKey);
    }

    @Test
    @DisplayName("매장 요약 캐시 저장 시 5분 TTL로 저장한다")
    void set_storesSummaryWithTtl() throws Exception {
        UUID storeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String cacheKey = "waiting:store-summary:" + storeId;
        StoreSummaryResponse summary = StoreSummaryResponse.of(storeId, "테스트 매장", ownerId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        repository.set(storeId, summary);

        verify(valueOperations).set(
                cacheKey,
                objectMapper.writeValueAsString(summary),
                Duration.ofMinutes(5)
        );
    }
}
