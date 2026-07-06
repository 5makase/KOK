package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreCategory;
import com.omakase.kok.store.domain.enums.StoreStatus;
import com.omakase.kok.store.domain.repository.StoreRepository;
import com.omakase.kok.store.domain.repository.StoreSearchCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class StoreRepositoryImpl implements StoreRepository {

    private final StoreJpaRepository storeJpaRepository;
    private final StoreQueryRepository storeQueryRepository;

    @Override
    public Store save(Store store) {
        return storeJpaRepository.save(store);
    }

    @Override
    public Optional<Store> findById(UUID storeId) {
        // soft delete 여부 무관하게 조회 — 복구/관리자 용도
        return storeJpaRepository.findById(storeId);
    }

    @Override
    public Optional<Store> findStore(UUID storeId) {
        // 활성 매장만 조회 (deletedAt IS NULL)
        return storeJpaRepository.findByStoreIdAndDeletedAtIsNull(storeId);
    }

    @Override
    public Page<Store> search(StoreSearchCondition condition, Pageable pageable) {
        // 카테고리·지역·편의시설·키워드 조건을 조합한 QueryDSL 동적 쿼리
        return storeQueryRepository.search(condition, pageable);
    }

    @Override
    public boolean existsActiveStoreByCategory(StoreCategory category) {
        return storeJpaRepository.existsByCategoryAndDeletedAtIsNull(category);
    }

    @Override
    public List<Store> findActiveStoresByIds(List<UUID> storeIds) {
        // 랭킹 응답 캐시 miss 시 호출 - category를 함께 로딩해 N+1 방지
        return storeJpaRepository.findActiveStoresByIdsWithCategory(storeIds);
    }

    @Override
    public List<Store> findAllRankableStores() {
        return storeJpaRepository.findRankableStoresWithCategory(StoreStatus.OPEN);
    }
}
