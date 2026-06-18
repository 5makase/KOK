package com.omakase.kok.store.application.result;

import com.omakase.kok.store.domain.entity.Store;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class StoreSummaryResult {

    private UUID storeId;
    private String storeName;
    private UUID ownerId;

    public static StoreSummaryResult from(Store store) {
        return StoreSummaryResult.builder()
                .storeId(store.getStoreId())
                .storeName(store.getName())
                .ownerId(store.getOwnerId())
                .build();
    }
}
