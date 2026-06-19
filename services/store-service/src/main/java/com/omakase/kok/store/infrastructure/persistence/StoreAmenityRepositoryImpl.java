package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreAmenity;
import com.omakase.kok.store.domain.enums.AmenityType;
import com.omakase.kok.store.domain.repository.StoreAmenityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class StoreAmenityRepositoryImpl implements StoreAmenityRepository {

    private final StoreAmenityJpaRepository storeAmenityJpaRepository;

    @Override
    public StoreAmenity save(StoreAmenity amenity) {
        return storeAmenityJpaRepository.save(amenity);
    }

    @Override
    public Optional<StoreAmenity> findAmenity(UUID storeId, UUID amenityId) {
        return storeAmenityJpaRepository.findByStoreStoreIdAndAmenityIdAndDeletedAtIsNull(storeId, amenityId);
    }

    @Override
    public List<StoreAmenity> findAllAmenities(Store store) {
        // 해당 매장의 활성 편의시설 전체 조회
        return storeAmenityJpaRepository.findAllByStoreAndDeletedAtIsNull(store);
    }

    @Override
    public Optional<StoreAmenity> findAmenityByType(Store store, AmenityType amenityType) {
        // 타입으로 편의시설 조회 — soft delete된 로우도 포함 (restore 패턴용)
        return storeAmenityJpaRepository.findByStoreAndAmenityType(store, amenityType);
    }
}
