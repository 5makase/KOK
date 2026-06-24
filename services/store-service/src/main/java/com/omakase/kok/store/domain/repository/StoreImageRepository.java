package com.omakase.kok.store.domain.repository;

import com.omakase.kok.store.domain.entity.StoreImage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreImageRepository {

    StoreImage save(StoreImage image);

    List<StoreImage> saveAll(List<StoreImage> images);

    // 이미지 식별자 기반 단건 조회 (soft delete 포함) - storeId + imageId 복합 조건으로 교차 접근 차단
    // 수정/삭제 전 상태 확인 목적으로 사용하며, 삭제 여부 판단은 호출 측에서 처리
    Optional<StoreImage> findImageById(UUID storeId, UUID imageId);

    // displayOrder로 조회 - soft delete 포함 (restore 패턴 및 슬롯 충돌 확인용)
    Optional<StoreImage> findImageByDisplayOrder(UUID storeId, int displayOrder);

    // 요청 슬롯 목록 한 번에 조회 - soft delete 포함 (bulk upsert용)
    List<StoreImage> findAllByDisplayOrders(UUID storeId, List<Integer> displayOrders);

    // 슬롯 교체 전 기존 이미지를 즉시 비움 - 새 이미지가 해당 슬롯을 차지할 수 있게 partial index에서 제외
    StoreImage evictImageSlot(StoreImage image);

    // 매장의 활성 이미지 목록 조회 (displayOrder 오름차순)
    List<StoreImage> findAllImages(UUID storeId);

    // 매장 상세 - 이미지 미리보기 (displayOrder 오름차순 최대 5개)
    List<StoreImage> findImagePreview(UUID storeId);
}
