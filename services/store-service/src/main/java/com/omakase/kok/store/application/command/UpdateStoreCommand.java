package com.omakase.kok.store.application.command;

import com.omakase.kok.store.domain.vo.Address;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UpdateStoreCommand {

    private UUID storeId;
    private UUID requesterId; // Gateway 헤더(X-User-Id)에서 추출 — 소유자 검증에 사용
    private UUID categoryId;
    private String name;
    private String phone;
    private Address address;
    private String description;
    private Integer maxCapacity;
}
