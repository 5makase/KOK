package com.omakase.kok.store.application;

import com.omakase.kok.store.application.cache.StoreListCacheRepository;
import com.omakase.kok.store.application.cache.StoreRankingCacheRepository;
import com.omakase.kok.store.application.result.StoreRankingResult;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.repository.StoreRankingRepository;
import com.omakase.kok.store.domain.repository.StoreRepository;
import com.omakase.kok.store.global.util.TransactionUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreRatingService {

    private final StoreRepository storeRepository;
    private final StoreRankingRepository storeRankingRepository;
    private final StoreListCacheRepository storeListCacheRepository;
    private final StoreRankingCacheRepository storeRankingCacheRepository;
    private final MeterRegistry meterRegistry;

    // Redis 장애 시에도 DB로 fallback해 API 자체는 계속 응답하도록
    // @Transactional: store.getCategory()가 LAZY이므로 트랜잭션 범위 필수
    @Transactional(readOnly = true)
    public List<StoreRankingResult> getRanking(int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);

        // 1) 응답 전체가 이미 캐싱돼 있으면 그대로 반환
        Optional<List<StoreRankingResult>> cached = storeRankingCacheRepository.get(safeSize);
        if (cached.isPresent()) {
            countRankingCacheResult("hit");
            return cached.get();
        }

        // 2) 캐시가 없으면 순위(매장 ID 목록) 조회. Redis 연결 자체가 안 되면 empty -> DB만 조회, 재구성은 시도하지 않음
        Optional<List<UUID>> rankedIdsOpt = getTopRankingOrEmpty(safeSize);
        if (rankedIdsOpt.isEmpty()) {
            countRankingCacheResult("redis_down");
            return readRankingFromDbOnly(safeSize);
        }

        // 2-2) Redis는 정상 응답했지만 빈 배열[] 응답(데이터 유실) -> DB 기준으로 Redis까지 재구성
        List<UUID> rankedIds = rankedIdsOpt.get();
        if (rankedIds.isEmpty()) {
            countRankingCacheResult("rebuild");
            return rebuildRankingFromDb(safeSize);
        }
        countRankingCacheResult("miss");

        // 3) 정상 케이스 -> Redis 순서(rank)를 보존하며 매장 상세 조회
        List<StoreRankingResult> results = buildResultsInRedisOrder(rankedIds);

        // Cache Miss: 조회 결과 캐싱
        storeRankingCacheRepository.set(safeSize, results);
        return results;
    }

    // Redis 연결 자체가 안 되는 상황과 정상 응답(빈 배열 포함)을 구분하기 위해 Optional.empty()를 예외 시그널로 사용
    private Optional<List<UUID>> getTopRankingOrEmpty(int safeSize) {
        try {
            return Optional.of(storeRankingRepository.getTopRanking(safeSize));
        } catch (RuntimeException e) {
            log.warn("store:ranking 조회 실패 - DB 직접 조회로 응답. size={}", safeSize, e);
            return Optional.empty();
        }
    }

    // Redis 순서(rank)를 보존하기 위해 Map으로 조회 후 rankedIds 순서대로 재정렬
    // storeMap에 없는(비활성/삭제) 매장을 먼저 걸러낸 뒤 rank를 매겨야 앞순위 결측 시에도 1위부터 연속됨
    private List<StoreRankingResult> buildResultsInRedisOrder(List<UUID> rankedIds) {
        Map<UUID, Store> storeMap = storeRepository.findActiveStoresByIds(rankedIds)
                .stream().collect(Collectors.toMap(Store::getStoreId, s -> s));

        List<UUID> activeRankedIds = rankedIds.stream()
                .filter(storeMap::containsKey)
                .toList();

        return IntStream.range(0, activeRankedIds.size())
                .mapToObj(i -> StoreRankingResult.of(i + 1, storeMap.get(activeRankedIds.get(i))))
                .toList();
    }

    // Redis의 순위 데이터가 통째로 비어있을 때(Redis 자체는 정상 응답한 상태) 호출된다.
    // DB에서 순위를 다시 계산해 응답을 만들고, 그중 한 요청만 Redis에 순위 데이터를 다시 채워 넣는다(재구성).
    private List<StoreRankingResult> rebuildRankingFromDb(int safeSize) {
        List<Store> rankableStores = storeRepository.findAllRankableStores();
        if (rankableStores.isEmpty()) return List.of();

        List<StoreRankingResult> results = buildTopResults(rankableStores, safeSize);

        if (storeRankingRepository.tryAcquireRebuildLock()) {
            Map<UUID, BigDecimal> scores = rankableStores.stream()
                    .collect(Collectors.toMap(Store::getStoreId, Store::getAverageRating));
            storeRankingRepository.rebuildAll(scores);
            storeRankingCacheRepository.set(safeSize, results);
            log.info("store:ranking 재구성 완료. 대상 매장 수={}", rankableStores.size());
        }
        return results;
    }

    // Redis 장애 시 DB 조회 - 이번 응답(상위 safeSize 개)만 만들면 되므로 전체를 긁지 않고 개수 제한 조회
    private List<StoreRankingResult> readRankingFromDbOnly(int safeSize) {
        return buildTopResults(storeRepository.findTopRankableStores(safeSize), safeSize);
    }

    private List<StoreRankingResult> buildTopResults(List<Store> rankableStores, int safeSize) {
        return IntStream.range(0, Math.min(safeSize, rankableStores.size()))
                .mapToObj(i -> StoreRankingResult.of(i + 1, rankableStores.get(i)))
                .toList();
    }

    @Transactional
    public void updateRating(UUID storeId, BigDecimal averageRating, Integer reviewCount) {
        Optional<Store> storeOpt = storeRepository.findById(storeId);
        // findActiveOrThrow() 대신 findById() 사용 - 예외 발생 시 Kafka 재시도 루프 방지
        if (storeOpt.isEmpty()) {
            log.info("존재하지 않는 매장 평점 갱신 skip. storeId={}", storeId);
            countRatingUpdateSkip("not_found");
            return;
        }
        Store store = storeOpt.get();
        // deleteStore()는 status 변경 없이 deletedAt만 세팅하므로, OPEN 여부와 별개로 isDeleted()도 확인해야 함
        // isDeleted()==true인 경우가 곧 폐업(PERMANENTLY_CLOSED) - 폐업 이벤트 빈도를 not_open(준비중·휴무)과 분리 집계하기 위해 태그를 나눔
        if (store.isDeleted()) {
            log.info("폐업 매장 평점 갱신 skip. storeId={}", storeId);
            countRatingUpdateSkip("deleted");
            return;
        }
        if (!store.isAvailableForService()) {
            log.info("영업중 아닌 매장 평점 갱신 skip. storeId={}, status={}", storeId, store.getStatus());
            countRatingUpdateSkip("not_open");
            return;
        }
        store.updateRating(averageRating, reviewCount);
        storeRepository.save(store);

        // DB 커밋 후 Redis 갱신 - 커밋 실패 시 Redis 불일치 방지
        registerRankingUpdateOnCommit(storeId, store.getAverageRating(), store.isRankable());
    }

    // reason=not_found(존재하지 않는 매장) / deleted(폐업) / not_open(준비중, 휴무)
    // 폐업 매장 이벤트 발생 빈도를 다른 비활성 사유와 분리 집계해 방어 코드 실효성 판단
    private void countRatingUpdateSkip(String reason) {
        Counter.builder("store.rating.update.skip")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    // result=hit(응답 캐시 적중) / miss(ZSet은 있으나 응답 캐시 없음) / redis_down(Redis 연결 실패) / rebuild(ZSet 유실, DB로 재구성)
    // 캐싱 or fallback이 실제로 의도대로 동작하는지 운영 중 직접 확인하기 위한 지표
    private void countRankingCacheResult(String result) {
        Counter.builder("store.ranking.cache.result")
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    // store:list:* 무효화도 함께 처리 - 평점 변경이 목록 정렬(평점순)에 영향을 주기 때문
    private void registerRankingUpdateOnCommit(UUID storeId, BigDecimal averageRating, boolean rankable) {
        TransactionUtils.runAfterCommit(
                () -> applyRankingUpdateAndEvictCache(storeId, averageRating, rankable)
        );
    }

    private void applyRankingUpdate(UUID storeId, BigDecimal averageRating, boolean rankable) {
        if (rankable) {
            storeRankingRepository.updateScore(storeId, averageRating);
        } else {
            storeRankingRepository.remove(storeId);
        }
    }

    private void applyRankingUpdateAndEvictCache(UUID storeId, BigDecimal averageRating, boolean rankable) {
        try {
            applyRankingUpdate(storeId, averageRating, rankable);
            log.debug("store:ranking 갱신 완료. storeId={}, rankable={}", storeId, rankable);
        } catch (RuntimeException e) {
            // TODO (고도화/도전기능) Spring Batch로 DB ↔ Redis 랭킹 주기적 재동기화 시 복구
            log.warn("store:ranking 갱신 실패. storeId={}, averageRating={}", storeId, averageRating, e);
        } finally {
            // ZSet(순위) 갱신 성공/실패와 무관하게 응답 캐시 무효화
            // 실패 시에도 stale 캐시를 남기면 잘못된 순위가 그대로 서빙되기 때문
            storeRankingCacheRepository.evictAll();
            storeListCacheRepository.evictAll();
        }
    }
}
