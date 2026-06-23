package com.omakase.kok.store.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omakase.kok.store.application.result.StoreResult;
import com.omakase.kok.store.domain.enums.AmenityType;
import com.omakase.kok.store.domain.repository.StoreSearchCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreListRedisRepositoryTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    // 실제 ObjectMapper로 직렬화/역직렬화 로직 검증
    @Spy ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    StoreListRedisRepository repository;

    private StoreSearchCondition condition;
    private PageRequest pageable;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        condition = StoreSearchCondition.builder().sido("서울").build();
        pageable = PageRequest.of(0, 20);
    }

    // get

    @Test
    @DisplayName("캐시 미스 - Redis null 반환 시 Optional.empty()")
    void get_cache_miss_returns_empty() {
        when(valueOperations.get(anyString())).thenReturn(null);

        Optional<Page<StoreResult>> result = repository.get(condition, pageable);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("역직렬화 실패(잘못된 JSON) 시 Optional.empty() - 예외 전파 없음")
    void get_invalid_json_returns_empty_without_throw() {
        when(valueOperations.get(anyString())).thenReturn("{invalid-json}");

        assertThatCode(() -> repository.get(condition, pageable))
                .doesNotThrowAnyException();
        assertThat(repository.get(condition, pageable)).isEmpty();
    }

    @Test
    @DisplayName("Redis 조회 중 예외 발생 시 Optional.empty() - DB fallback")
    void get_redis_exception_returns_empty() {
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis 연결 실패"));

        assertThatCode(() -> repository.get(condition, pageable))
                .doesNotThrowAnyException();
        assertThat(repository.get(condition, pageable)).isEmpty();
    }

    // set

    @Test
    @DisplayName("set - TTL 300초로 저장")
    void set_stores_with_300s_ttl() {
        Page<StoreResult> page = new PageImpl<>(List.of(), pageable, 0);

        repository.set(condition, pageable, page);

        verify(valueOperations).set(anyString(), anyString(),
                eq(java.time.Duration.ofSeconds(300)));
    }

    @Test
    @DisplayName("Redis 저장 실패 시 예외 전파 없음 (캐싱 생략)")
    void set_redis_failure_does_not_throw() {
        doThrow(new RuntimeException("Redis down"))
                .when(valueOperations).set(anyString(), anyString(), any());
        Page<StoreResult> page = new PageImpl<>(List.of(), pageable, 0);

        assertThatCode(() -> repository.set(condition, pageable, page))
                .doesNotThrowAnyException();
    }

    // evictAll

    @Test
    @DisplayName("evictAll - SCAN으로 store:list:* 키 수집 후 일괄 삭제")
    void evictAll_scans_and_deletes_matching_keys() {
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn("store:list:key1", "store:list:key2");
        // Iterator.forEachRemaining은 default 메서드 → Mockito 모킹 시 no-op으로 처리됨
        // doCallRealMethod로 실제 default 구현(hasNext/next 루프) 사용
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
    @DisplayName("evictAll - Redis 예외 발생해도 전파 없음")
    void evictAll_redis_exception_does_not_throw() {
        when(redisTemplate.scan(any(ScanOptions.class))).thenThrow(new RuntimeException("Redis down"));

        assertThatCode(() -> repository.evictAll())
                .doesNotThrowAnyException();
    }

    // buildKey

    @Test
    @DisplayName("캐시 키 - 편의시설 요청 순서 달라도 동일 키 생성 (정렬 보장)")
    void buildKey_amenity_order_independent() {
        StoreSearchCondition c1 = StoreSearchCondition.builder()
                .amenities(List.of(AmenityType.PARKING, AmenityType.WIFI)).build();
        StoreSearchCondition c2 = StoreSearchCondition.builder()
                .amenities(List.of(AmenityType.WIFI, AmenityType.PARKING)).build();

        when(valueOperations.get(anyString())).thenReturn(null);
        repository.get(c1, pageable);
        repository.get(c2, pageable);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, times(2)).get(captor.capture());
        assertThat(captor.getAllValues().get(0)).isEqualTo(captor.getAllValues().get(1));
    }

    @Test
    @DisplayName("캐시 키 - 조건 미지정 필드는 ALL로 채워 키 충돌 방지")
    void buildKey_null_fields_become_all() {
        StoreSearchCondition empty = StoreSearchCondition.builder().build();
        when(valueOperations.get(anyString())).thenReturn(null);

        repository.get(empty, pageable);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).get(captor.capture());
        assertThat(captor.getValue())
                .startsWith("store:list:")
                .contains("ALL");
    }

    @Test
    @DisplayName("캐시 키 - 페이지 번호/크기가 키에 반영됨")
    void buildKey_includes_page_info() {
        PageRequest p1 = PageRequest.of(0, 20);
        PageRequest p2 = PageRequest.of(1, 20);
        when(valueOperations.get(anyString())).thenReturn(null);

        repository.get(condition, p1);
        repository.get(condition, p2);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, times(2)).get(captor.capture());
        assertThat(captor.getAllValues().get(0)).isNotEqualTo(captor.getAllValues().get(1));
    }
}
