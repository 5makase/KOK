package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreAmenity;
import com.omakase.kok.store.domain.enums.AmenityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreAmenityJpaRepository extends JpaRepository<StoreAmenity, UUID> {

    // storeId + amenityId 복합 조건 - 다중 매장 점주의 교차 접근 차단
    Optional<StoreAmenity> findByStoreStoreIdAndAmenityIdAndDeletedAtIsNull(UUID storeId, UUID amenityId);

    List<StoreAmenity> findAllByStoreAndDeletedAtIsNull(Store store);

    Optional<StoreAmenity> findByStoreAndAmenityType(Store store, AmenityType amenityType);
}
