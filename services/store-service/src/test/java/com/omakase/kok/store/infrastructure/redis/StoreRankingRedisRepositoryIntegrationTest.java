package com.omakase.kok.store.infrastructure.redis;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StoreRankingRedisRepository 통합 테스트 - Redis 사용
 * 실행 전제: docker compose up -d redis (application-test.yml: localhost:6379)
 */
@SpringBootTest
@ActiveProfiles("test")
class StoreRankingRedisRepositoryIntegrationTest {

    @Autowired StoreRankingRedisRepository repository;
    @Autowired RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void cleanUp() {
        redisTemplate.delete("store:ranking");
    }

    @Test
    @DisplayName("updateScore - 점수 저장 후 reverseRange로 조회 가능")
    void updateScore_and_getTopRanking() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        repository.updateScore(id1, new BigDecimal("4.50"));
        repository.updateScore(id2, new BigDecimal("4.80"));

        List<UUID> ranking = repository.getTopRanking(2);

        assertThat(ranking).hasSize(2);
        // 높은 점수(4.80)가 1위
        assertThat(ranking.get(0)).isEqualTo(id2);
        assertThat(ranking.get(1)).isEqualTo(id1);
    }

    @Test
    @DisplayName("updateScore - 같은 storeId에 새 점수 덮어씀 (멱등)")
    void updateScore_overwrites_existing_score() {
        UUID id = UUID.randomUUID();
        repository.updateScore(id, new BigDecimal("3.00"));
        repository.updateScore(id, new BigDecimal("4.90"));

        List<UUID> ranking = repository.getTopRanking(1);
        assertThat(ranking).containsExactly(id);

        Long size = redisTemplate.opsForZSet().size("store:ranking");
        assertThat(size).isEqualTo(1);
    }

    @Test
    @DisplayName("remove - 랭킹에서 제거 후 조회 불가")
    void remove_deletes_from_ranking() {
        UUID id = UUID.randomUUID();
        repository.updateScore(id, new BigDecimal("4.50"));
        assertThat(repository.getTopRanking(1)).containsExactly(id);

        repository.remove(id);

        assertThat(repository.getTopRanking(1)).isEmpty();
    }

    @Test
    @DisplayName("getTopRanking - size만큼만 반환")
    void getTopRanking_respects_size_limit() {
        for (int i = 0; i < 5; i++) {
            repository.updateScore(UUID.randomUUID(), new BigDecimal(i + ".00"));
        }

        List<UUID> ranking = repository.getTopRanking(3);

        assertThat(ranking).hasSize(3);
    }

    @Test
    @DisplayName("getTopRanking - 데이터 없으면 빈 리스트")
    void getTopRanking_empty_when_no_data() {
        assertThat(repository.getTopRanking(10)).isEmpty();
    }
}
