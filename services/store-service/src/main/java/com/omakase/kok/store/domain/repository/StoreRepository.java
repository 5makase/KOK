package com.omakase.kok.store.domain.repository;

import com.omakase.kok.store.domain.entity.Store;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface StoreRepository {

    Store save(Store store);

    // 삭제 포함 단건 조회
    Optional<Store> findById(UUID storeId);

    // 활성 매장 단건 조회
    Optional<Store> findStore(UUID storeId);

    // 조건 기반 매장 목록 검색
    Page<Store> search(StoreSearchCondition condition, Pageable pageable);
}
