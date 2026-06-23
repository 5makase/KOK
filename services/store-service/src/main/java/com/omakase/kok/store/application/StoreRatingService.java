package com.omakase.kok.store.application;

import com.omakase.kok.store.application.cache.StoreListCacheRepository;
import com.omakase.kok.store.application.result.StoreRankingResult;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.repository.StoreRankingRepository;
import com.omakase.kok.store.domain.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    // @Transactional: store.getCategory()가 LAZY이므로 트랜잭션 범위 필수
    @Transactional(readOnly = true)
    public List<StoreRankingResult> getRanking(int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        List<UUID> rankedIds = storeRankingRepository.getTopRanking(safeSize);
        if (rankedIds.isEmpty()) {
            return List.of();
        }
        // Redis 순서(rank)를 보존하기 위해 Map으로 조회 후 rankedIds 순서대로 재정렬
        Map<UUID, Store> storeMap = storeRepository.findActiveStoresByIds(rankedIds)
                .stream().collect(Collectors.toMap(Store::getStoreId, s -> s));

        return IntStream.range(0, rankedIds.size())
                .filter(i -> storeMap.containsKey(rankedIds.get(i)))
                .mapToObj(i -> StoreRankingResult.of(i + 1, storeMap.get(rankedIds.get(i))))
                .toList();
    }

    @Transactional
    public void updateRating(UUID storeId, BigDecimal averageRating, Integer reviewCount) {
        Optional<Store> storeOpt = storeRepository.findById(storeId);
        // findActiveOrThrow() 대신 findById() 사용 - 예외 발생 시 Kafka 재시도 루프 방지
        if (storeOpt.isEmpty()) {
            log.info("존재하지 않는 매장 평점 갱신 skip. storeId={}", storeId);
            return;
        }
        Store store = storeOpt.get();
        // soft delete(deletedAt != null) 또는 OPEN이 아닌 매장은 평점 갱신 skip
        // deleteStore()는 status 변경 없이 deletedAt만 세팅하므로 isDeleted() 별도 체크 필요
        if (store.isDeleted() || !store.isAvailableForService()) {
            log.info("비활성 매장 평점 갱신 skip. storeId={}", storeId);
            return;
        }
        store.updateRating(averageRating, reviewCount);
        storeRepository.save(store);

        // DB 커밋 후 Redis 갱신 - 커밋 실패 시 Redis 불일치 방지
        // store.updateRating() 내부에서 반올림된 값을 Redis에도 동일하게 반영
        registerRankingUpdateOnCommit(storeId, store.getAverageRating(), store.getReviewCount());
    }

    // reviewCount == 0 이면 랭킹에서 제거, 그 외엔 점수 갱신
    // store:list:* 는 TTL(5분) 만료에 위임
    private void registerRankingUpdateOnCommit(UUID storeId, BigDecimal averageRating, Integer reviewCount) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            applyRankingUpdate(storeId, averageRating, reviewCount);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    applyRankingUpdate(storeId, averageRating, reviewCount);
                    log.debug("store:ranking 갱신 완료. storeId={}, reviewCount={}", storeId, reviewCount);
                } catch (RuntimeException e) {
                    // TODO (고도화/도전기능) Spring Batch로 DB ↔ Redis 랭킹 주기적 재동기화 시 복구
                    log.warn("store:ranking 갱신 실패. storeId={}, averageRating={}", storeId, averageRating, e);
                }
                // 평점 변경 시 목록 캐시 무효화 - 랭킹과 동일하게 afterCommit() 기준으로 통일
                storeListCacheRepository.evictAll();
            }
        });
    }

    private void applyRankingUpdate(UUID storeId, BigDecimal averageRating, Integer reviewCount) {
        // reviewCount == 0: 마지막 리뷰 삭제로 리뷰가 없는 상태 → 랭킹에서 제거
        // averageRating 값(0 또는 null)에 무관하게 reviewCount 기준으로 분기
        if (reviewCount == 0) {
            storeRankingRepository.remove(storeId);
        } else {
            storeRankingRepository.updateScore(storeId, averageRating);
        }
    }
}
