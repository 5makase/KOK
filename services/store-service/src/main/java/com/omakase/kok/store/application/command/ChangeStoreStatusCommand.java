package com.omakase.kok.store.application.command;

import com.omakase.kok.store.domain.enums.StoreStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ChangeStoreStatusCommand {

    private UUID storeId;
    private UUID requesterId; // 소유자 검증 대상 — MASTER면 스킵
    private StoreStatus status; // 상태 전이 유효성은 Store.changeStatus()에서 검증
    private String role;
}
