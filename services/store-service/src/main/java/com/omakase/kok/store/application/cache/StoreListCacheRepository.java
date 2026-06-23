package com.omakase.kok.store.application.cache;

import com.omakase.kok.store.application.result.StoreResult;
import com.omakase.kok.store.domain.repository.StoreSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface StoreListCacheRepository {
    Optional<Page<StoreResult>> get(StoreSearchCondition condition, Pageable pageable);
    void set(StoreSearchCondition condition, Pageable pageable, Page<StoreResult> page);
    void evictAll();
}
