package com.omakase.kok.store.infrastructure.redis;

import com.omakase.kok.store.domain.repository.StoreRankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class StoreRankingRedisRepository implements StoreRankingRepository {

    private static final String RANKING_KEY = "store:ranking";
    // ZSet 재구성 동시 폭주 방지용 가드 키 - 짧은 TTL로 자동 해제 (재구성은 수 ms~수십 ms면 끝나는 작업)
    private static final String REBUILD_LOCK_KEY = "store:ranking:rebuild:lock";
    private static final Duration REBUILD_LOCK_TTL = Duration.ofSeconds(3);

    private final RedisTemplate<String, String> redisTemplate;

    // 매장 하나의 랭킹 점수를 등록하거나 갱신한다. 이미 등록된 매장이면 점수만 새 값으로 덮어쓴다.
    public void updateScore(UUID storeId, BigDecimal averageRating) {
        redisTemplate.opsForZSet().add(RANKING_KEY, storeId.toString(), averageRating.doubleValue());
    }

    // 매장 하나를 랭킹에서 제거한다 (리뷰가 모두 없어져 더 이상 순위에 노출되면 안 될 때 호출됨)
    public void remove(UUID storeId) {
        redisTemplate.opsForZSet().remove(RANKING_KEY, storeId.toString());
    }

    // 점수가 높은 순으로 상위 size개 매장 ID를 반환한다.
    // Redis 연결 자체가 안 되는 경우에는 예외를 그대로 호출부로 전달
    public List<UUID> getTopRanking(int size) {
        if (size <= 0) return Collections.emptyList();
        Set<String> result = redisTemplate.opsForZSet().reverseRange(RANKING_KEY, 0, size - 1L);
        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }
        return result.stream().map(UUID::fromString).toList();
    }

    // 랭킹 데이터를 다시 만드는 작업을 지금 이 요청이 맡아도 되는지 확인
    // 여러 요청이 동시에 몰려도 그중 하나만 재구성을 수행하도록 -> 일정 시간 후 자동 풀림
    @Override
    public boolean tryAcquireRebuildLock() {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(REBUILD_LOCK_KEY, "1", REBUILD_LOCK_TTL);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            // 잠금 확인 자체가 실패해도 호출부는 재구성만 생략하고 계속 진행할 수 있으므로 false로 처리
            log.warn("store:ranking 재구성 잠금 확인 실패 - 이번 요청은 재구성 생략", e);
            return false;
        }
    }

    // 전달받은 "매장 ID - 평점" 목록으로 랭킹 데이터를 통째로 생성
    @Override
    public void rebuildAll(Map<UUID, BigDecimal> storeScores) {
        if (storeScores.isEmpty()) return;
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples = storeScores.entrySet().stream()
                    .map(entry -> ZSetOperations.TypedTuple.of(entry.getKey().toString(), entry.getValue().doubleValue()))
                    .collect(Collectors.toSet());
            redisTemplate.opsForZSet().add(RANKING_KEY, tuples);
        } catch (Exception e) {
            // 재구성 도중 실패해도 호출부는 이미 계산해 둔 응답을 그대로 돌려주면 되므로 예외를 전파하지 않는다
            log.warn("store:ranking 재구성 실패 - 다음 요청에서 다시 시도됨", e);
        }
    }
}
