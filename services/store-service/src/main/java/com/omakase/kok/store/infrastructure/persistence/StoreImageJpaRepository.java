package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreImageJpaRepository extends JpaRepository<StoreImage, UUID> {

    // storeId + imageId 복합 조건 - 다중 매장 점주의 교차 접근 차단
    Optional<StoreImage> findByStoreStoreIdAndImageIdAndDeletedAtIsNull(UUID storeId, UUID imageId);

    // displayOrder로 조회 - soft delete 포함 (restore 패턴 및 슬롯 충돌 확인용)
    Optional<StoreImage> findByStoreAndDisplayOrder(Store store, int displayOrder);

    List<StoreImage> findAllByStoreAndDeletedAtIsNullOrderByDisplayOrderAsc(Store store);
}
