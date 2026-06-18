package com.omakase.kok.store.domain.repository;

import com.omakase.kok.store.domain.entity.StoreCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreCategoryRepository {

    StoreCategory save(StoreCategory category);

    // 삭제 포함 단건 조회
    Optional<StoreCategory> findById(UUID categoryId);

    // 활성 카테고리 단건 조회
    Optional<StoreCategory> findCategory(UUID categoryId);

    // 활성 카테고리 전체 목록 (1뎁스 기준 트리 구조)
    List<StoreCategory> findAllCategories();
}
