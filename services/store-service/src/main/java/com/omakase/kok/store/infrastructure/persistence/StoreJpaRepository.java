package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoreJpaRepository extends JpaRepository<Store, UUID> {

    Optional<Store> findByStoreIdAndDeletedAtIsNull(UUID storeId);

    // 해당 카테고리에 활성 매장 존재 여부
    boolean existsByCategoryAndDeletedAtIsNull(StoreCategory category);
}
