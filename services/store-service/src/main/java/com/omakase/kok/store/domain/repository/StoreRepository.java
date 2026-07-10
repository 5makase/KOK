package com.omakase.kok.store.domain.repository;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreRepository {

    Store save(Store store);

    // 삭제 포함 단건 조회
    Optional<Store> findById(UUID storeId);

    // 활성 매장 단건 조회
    Optional<Store> findStore(UUID storeId);

    // 조건 기반 매장 목록 검색
    Page<Store> search(StoreSearchCondition condition, Pageable pageable);

    // 해당 카테고리에 활성 매장 존재 여부 (소분류 삭제 전 체크)
    boolean existsActiveStoreByCategory(StoreCategory category);

    // 랭킹 조회용 - storeId 목록으로 활성 매장 일괄 조회
    List<Store> findActiveStoresByIds(List<UUID> storeIds);

    // 랭킹에 노출될 수 있는 매장 전체를 평점이 높은 순서로 조회: ZSet 재구성처럼 전체가 필요할 때 사용
    List<Store> findAllRankableStores();

    // 상위 limit개만 조회: Redis 자체가 응답 불가할 때, 일회성 응답만 만들면 되는 경우 사용
    List<Store> findTopRankableStores(int limit);
}
