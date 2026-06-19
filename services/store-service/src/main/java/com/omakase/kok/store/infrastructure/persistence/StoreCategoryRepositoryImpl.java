package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.StoreCategory;
import com.omakase.kok.store.domain.repository.StoreCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class StoreCategoryRepositoryImpl implements StoreCategoryRepository {

    private final StoreCategoryJpaRepository storeCategoryJpaRepository;

    @Override
    public StoreCategory save(StoreCategory category) {
        return storeCategoryJpaRepository.save(category);
    }

    @Override
    public Optional<StoreCategory> findById(UUID categoryId) {
        // soft delete 여부 무관하게 조회
        return storeCategoryJpaRepository.findById(categoryId);
    }

    @Override
    public Optional<StoreCategory> findCategory(UUID categoryId) {
        // 활성 카테고리만 조회 (deletedAt IS NULL)
        return storeCategoryJpaRepository.findByCategoryIdAndDeletedAtIsNull(categoryId);
    }

    @Override
    public List<StoreCategory> findAllCategories() {
        // 1뎁스 루트 카테고리와 하위 카테고리를 함께 조회
        return storeCategoryJpaRepository.findAllRootCategoriesWithChildren();
    }

    @Override
    public List<StoreCategory> findAllCategoriesIncludingDeleted() {
        return storeCategoryJpaRepository.findAllRootCategoriesWithChildrenIncludingDeleted();
    }

    @Override
    public boolean existsActiveChildren(StoreCategory parent) {
        return storeCategoryJpaRepository.existsByParentAndDeletedAtIsNull(parent);
    }
}
