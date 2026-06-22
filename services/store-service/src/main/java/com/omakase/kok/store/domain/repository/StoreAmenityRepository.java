package com.omakase.kok.store.domain.repository;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreAmenity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreAmenityRepository {

    StoreAmenity save(StoreAmenity amenity);

    void saveAll(List<StoreAmenity> amenities);

    // 매장 소속 편의시설 단건 조회 (soft delete 포함) - storeId + amenityId 복합 조건으로 교차 접근 차단
    Optional<StoreAmenity> findAmenity(UUID storeId, UUID amenityId);

    // 매장의 활성 편의시설 목록 조회
    List<StoreAmenity> findAllAmenities(Store store);

    // 매장의 전체 편의시설 조회 (soft delete 포함) - sync 처리용
    List<StoreAmenity> findAllAmenitiesIncludingDeleted(Store store);
}
