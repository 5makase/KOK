package com.omakase.kok.store.application.command;

import com.omakase.kok.store.domain.enums.StoreStatus;
import com.omakase.kok.store.presentation.dto.request.ChangeStoreStatusRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ChangeStoreStatusCommand {

    private UUID storeId;
    private UUID requesterId; // 소유자 검증용 — OWNER 본인 또는 MASTER만 허용
    private StoreStatus status; // 상태 전이 유효성 검증은 Store.changeStatus()에서 처리

    public static ChangeStoreStatusCommand of(UUID storeId, UUID requesterId, ChangeStoreStatusRequest request) {
        return ChangeStoreStatusCommand.builder()
                .storeId(storeId)
                .requesterId(requesterId)
                .status(request.getStatus())
                .build();
    }
}
