package com.omakase.kok.store.domain.repository;

import com.omakase.kok.store.domain.entity.StoreImage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreImageRepository {

    StoreImage save(StoreImage image);

    List<StoreImage> saveAll(List<StoreImage> images);

    // 매장 소속 활성 이미지 단건 조회 - storeId + imageId 복합 조건으로 교차 접근 차단
    Optional<StoreImage> findImage(UUID storeId, UUID imageId);

    // displayOrder로 조회 - soft delete 포함 (restore 패턴 및 슬롯 충돌 확인용)
    Optional<StoreImage> findImageByDisplayOrder(UUID storeId, int displayOrder);

    // 요청 슬롯 목록 한 번에 조회 - soft delete 포함 (bulk upsert용)
    List<StoreImage> findAllByDisplayOrders(UUID storeId, List<Integer> displayOrders);

    // 매장의 활성 이미지 목록 조회 (displayOrder 오름차순)
    List<StoreImage> findAllImages(UUID storeId);
}
