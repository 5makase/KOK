package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreAmenity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreAmenityJpaRepository extends JpaRepository<StoreAmenity, UUID> {

    // storeId + amenityId 복합 조건 - 다중 매장 점주의 교차 접근 차단 (soft delete 포함 - 400 처리용)
    Optional<StoreAmenity> findByStoreStoreIdAndAmenityId(UUID storeId, UUID amenityId);

    // 매장의 활성 편의시설 목록 조회
    List<StoreAmenity> findAllByStoreAndDeletedAtIsNull(Store store);

    // 전체 편의시설 조회 (soft delete 포함) - sync 처리용
    List<StoreAmenity> findAllByStore(Store store);
}
