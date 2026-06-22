package com.omakase.kok.store.application.cache;

import com.omakase.kok.store.application.result.StoreResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface StoreListCacheRepository {
    Optional<Page<StoreResult>> get(String cacheKey, Pageable pageable);
    void set(String cacheKey, Page<StoreResult> page);
    void evictAll();
}
