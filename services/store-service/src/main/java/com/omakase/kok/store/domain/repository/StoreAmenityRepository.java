package com.omakase.kok.store.domain.repository;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreAmenity;
import com.omakase.kok.store.domain.enums.AmenityType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreAmenityRepository {

    StoreAmenity save(StoreAmenity amenity);

    // 매장 소속 활성 편의시설 단건 조회 - storeId + amenityId 복합 조건으로 교차 접근 차단
    Optional<StoreAmenity> findAmenity(UUID storeId, UUID amenityId);

    // 매장의 활성 편의시설 목록 조회
    List<StoreAmenity> findAllAmenities(Store store);

    // 편의시설 타입으로 조회 (삭제 포함 - restore 처리용)
    Optional<StoreAmenity> findAmenityByType(Store store, AmenityType amenityType);
}
