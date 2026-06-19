package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.StoreCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreCategoryJpaRepository extends JpaRepository<StoreCategory, UUID> {

    Optional<StoreCategory> findByCategoryIdAndDeletedAtIsNull(UUID categoryId);

    // 1뎁스 기준 전체 트리 조회 - 부모,자식 모두 활성(deletedAt IS NULL)만 포함
    @Query("SELECT DISTINCT c FROM StoreCategory c LEFT JOIN FETCH c.children ch WHERE c.parent IS NULL AND c.deletedAt IS NULL AND (ch IS NULL OR ch.deletedAt IS NULL)")
    List<StoreCategory> findAllRootCategoriesWithChildren();

    // soft delete 포함 전체 트리 조회 - MASTER 전용
    @Query("SELECT DISTINCT c FROM StoreCategory c LEFT JOIN FETCH c.children WHERE c.parent IS NULL")
    List<StoreCategory> findAllRootCategoriesWithChildrenIncludingDeleted();

    // 활성 자식 카테고리 존재 여부
    boolean existsByParentAndDeletedAtIsNull(StoreCategory parent);
}
