package com.omakase.kok.store.domain.service;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.repository.StoreRepository;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreFinder {

    private final StoreRepository storeRepository;

    // 활성 매장 단건 조회 - 삭제(soft delete) 또는 폐업(PERMANENTLY_CLOSED) 매장은 NOT_FOUND 처리
    public Store findActiveOrThrow(UUID storeId) {
        return storeRepository.findStore(storeId)
                .orElseThrow(() -> new BaseException(StoreErrorCode.STORE_NOT_FOUND));
    }
}
