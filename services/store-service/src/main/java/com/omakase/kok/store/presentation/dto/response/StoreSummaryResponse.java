package com.omakase.kok.store.presentation.dto.response;

import com.omakase.kok.store.application.result.StoreSummaryResult;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

// 서비스 간 내부 호출용 — 웨이팅 서비스에서 매장 기본 정보 조회 시 사용
@Getter
@Builder
public class StoreSummaryResponse {

    private UUID storeId;
    private String storeName;
    private UUID ownerId;

    public static StoreSummaryResponse from(StoreSummaryResult result) {
        return StoreSummaryResponse.builder()
                .storeId(result.getStoreId())
                .storeName(result.getStoreName())
                .ownerId(result.getOwnerId())
                .build();
    }
}
