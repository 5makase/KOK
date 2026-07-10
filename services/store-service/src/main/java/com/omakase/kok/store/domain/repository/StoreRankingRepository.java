package com.omakase.kok.store.domain.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface StoreRankingRepository {

    // 매장 하나의 랭킹 점수 등록 or 갱신
    void updateScore(UUID storeId, BigDecimal averageRating);

    // 매장 하나를 랭킹에서 제거
    void remove(UUID storeId);

    // 점수가 높은 순으로 상위 size개 매장 ID를 반환
    List<UUID> getTopRanking(int size);

    // 랭킹 데이터를 통째로 재구성하는 작업을 지금 이 요청이 수행해도 되는지 확인
    boolean tryAcquireRebuildLock();

    // 전달받은 "매장 ID - 평점" 목록으로 랭킹 데이터를 통째로 재구성
    void rebuildAll(Map<UUID, BigDecimal> storeScores);
}
