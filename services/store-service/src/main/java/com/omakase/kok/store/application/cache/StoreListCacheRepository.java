package com.omakase.kok.store.application.cache;

import com.omakase.kok.store.application.result.StoreResult;
import com.omakase.kok.store.domain.repository.StoreSearchCondition;
import com.omakase.kok.store.global.util.TransactionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface StoreListCacheRepository {
    Optional<Page<StoreResult>> get(StoreSearchCondition condition, Pageable pageable);
    void set(StoreSearchCondition condition, Pageable pageable, Page<StoreResult> page);
    void evictAll();

    // DB 커밋 완료 후 목록 캐시 무효화 -> 롤백 시 불필요한 eviction 방지
    default void evictAllAfterCommit() {
        TransactionUtils.runAfterCommit(this::evictAll);
    }
}
