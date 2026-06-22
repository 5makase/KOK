package com.omakase.kok.store.application;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.repository.StoreRepository;
import com.omakase.kok.store.domain.service.StoreFinder;
import com.omakase.kok.store.domain.repository.StoreRankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreRatingService {

    private final StoreFinder storeFinder;
    private final StoreRepository storeRepository;
    private final StoreRankingRepository storeRankingRepository;

    @Transactional
    public void updateRating(UUID storeId, BigDecimal averageRating, Integer reviewCount) {
        Store store = storeFinder.findActiveOrThrow(storeId);
        store.updateRating(averageRating, reviewCount);
        storeRepository.save(store);

        // DB 커밋 후 Redis 갱신 - 커밋 실패 시 Redis 불일치 방지
        scheduleRankingUpdateAfterCommit(storeId, averageRating, reviewCount);
    }

    // reviewCount == 0 이면 랭킹에서 제거, 그 외엔 점수 갱신
    // store:list:* 는 TTL(5분) 만료에 위임
    private void scheduleRankingUpdateAfterCommit(UUID storeId, BigDecimal averageRating, Integer reviewCount) {
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
