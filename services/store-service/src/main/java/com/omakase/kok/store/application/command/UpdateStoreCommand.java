package com.omakase.kok.store.application.command;

import com.omakase.kok.store.presentation.dto.request.UpdateStoreRequest;
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
    private String addressSido;
    private String addressSigungu;
    private String addressDong;
    private String addressDetail;
    private String description;
    private Integer maxCapacity;

    public static UpdateStoreCommand of(UUID storeId, UUID requesterId, UpdateStoreRequest request) {
        return UpdateStoreCommand.builder()
                .storeId(storeId)
                .requesterId(requesterId)
                .categoryId(request.getCategoryId())
                .name(request.getName())
                .phone(request.getPhone())
                .addressSido(request.getAddressSido())
                .addressSigungu(request.getAddressSigungu())
                .addressDong(request.getAddressDong())
                .addressDetail(request.getAddressDetail())
                .description(request.getDescription())
                .maxCapacity(request.getMaxCapacity())
                .build();
    }
}
