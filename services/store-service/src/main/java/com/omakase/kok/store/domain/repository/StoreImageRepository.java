package com.omakase.kok.store.domain.repository;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreImage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreImageRepository {

    StoreImage save(StoreImage image);

    // 매장 소속 활성 이미지 단건 조회 - storeId + imageId 복합 조건으로 교차 접근 차단
    Optional<StoreImage> findImage(UUID storeId, UUID imageId);

    // displayOrder로 조회 - soft delete 포함 (restore 패턴 및 슬롯 충돌 확인용)
    Optional<StoreImage> findImageByDisplayOrder(Store store, int displayOrder);

    // 매장의 활성 이미지 목록 조회 (displayOrder 오름차순)
    List<StoreImage> findAllImages(Store store);
}
