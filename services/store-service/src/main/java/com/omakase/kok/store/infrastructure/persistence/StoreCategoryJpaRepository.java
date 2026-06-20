package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.StoreCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreCategoryJpaRepository extends JpaRepository<StoreCategory, UUID> {

    Optional<StoreCategory> findByCategoryIdAndDeletedAtIsNull(UUID categoryId);

    // 1뎁스 기준 전체 트리 조회 - 활성 루트 + 활성 자식만 포함
    @Query("SELECT DISTINCT c FROM StoreCategory c LEFT JOIN FETCH c.children ch WHERE c.parent IS NULL AND c.deletedAt IS NULL AND ch.deletedAt IS NULL")
    List<StoreCategory> findAllRootCategoriesWithChildren();

    // 활성 자식 카테고리 존재 여부
    boolean existsByParentAndDeletedAtIsNull(StoreCategory parent);

    // 같은 부모 아래 동명 카테고리 중복 검사 (excludeId: create 시 null, update 시 자신 ID)
    @Query("SELECT COUNT(c) > 0 FROM StoreCategory c WHERE c.deletedAt IS NULL AND c.name = :name AND ((:parent IS NULL AND c.parent IS NULL) OR c.parent = :parent) AND (:excludeId IS NULL OR c.categoryId <> :excludeId)")
    boolean existsActiveSiblingByName(@Param("name") String name, @Param("parent") StoreCategory parent, @Param("excludeId") UUID excludeId);

    // 같은 부모 아래 sortOrder 중복 검사
    @Query("SELECT COUNT(c) > 0 FROM StoreCategory c WHERE c.deletedAt IS NULL AND c.sortOrder = :sortOrder AND ((:parent IS NULL AND c.parent IS NULL) OR c.parent = :parent) AND (:excludeId IS NULL OR c.categoryId <> :excludeId)")
    boolean existsActiveSiblingBySortOrder(@Param("sortOrder") int sortOrder, @Param("parent") StoreCategory parent, @Param("excludeId") UUID excludeId);
}
