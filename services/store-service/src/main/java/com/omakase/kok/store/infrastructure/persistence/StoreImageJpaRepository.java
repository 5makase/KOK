package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.StoreImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreImageJpaRepository extends JpaRepository<StoreImage, UUID> {

    // storeId + imageId 복합 조건 - 다중 매장 점주의 교차 접근 차단 (soft delete 포함)
    Optional<StoreImage> findByStoreStoreIdAndImageId(UUID storeId, UUID imageId);

    // 활성 슬롯만 조회 - 슬롯 충돌 확인용 (같은 displayOrder에 soft delete 행이 여러 개일 수 있어 활성 행만 조회)
    Optional<StoreImage> findByStoreStoreIdAndDisplayOrderAndDeletedAtIsNull(UUID storeId, int displayOrder);

    // 요청 슬롯 목록 한 번에 조회 - soft delete 포함 (bulk upsert용)
    List<StoreImage> findAllByStoreStoreIdAndDisplayOrderIn(UUID storeId, List<Integer> displayOrders);

    // 매장의 활성 이미지 목록 조회 (displayOrder 오름차순)
    List<StoreImage> findAllByStoreStoreIdAndDeletedAtIsNullOrderByDisplayOrderAsc(UUID storeId);

    // 매장 상세 - 이미지 미리보기 (displayOrder 오름차순 최대 5개)
    List<StoreImage> findTop5ByStoreStoreIdAndDeletedAtIsNullOrderByDisplayOrderAsc(UUID storeId);
}
