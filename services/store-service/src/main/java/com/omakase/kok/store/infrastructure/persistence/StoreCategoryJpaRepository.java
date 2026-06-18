package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.StoreCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreCategoryJpaRepository extends JpaRepository<StoreCategory, UUID> {

    Optional<StoreCategory> findByCategoryIdAndDeletedAtIsNull(UUID categoryId);

    // 1뎁스 기준 전체 트리 조회 (children LAZY 로딩 방지)
    @Query("SELECT c FROM StoreCategory c LEFT JOIN FETCH c.children WHERE c.parent IS NULL AND c.deletedAt IS NULL")
    List<StoreCategory> findAllRootCategoriesWithChildren();
}
