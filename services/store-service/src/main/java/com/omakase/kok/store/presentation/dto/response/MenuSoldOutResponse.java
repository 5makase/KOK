package com.omakase.kok.store.presentation.dto.response;

import com.omakase.kok.store.application.result.MenuResult;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class MenuSoldOutResponse {

    private UUID menuId;
    private boolean isSoldOut;
    private LocalDateTime updatedAt;

    public static MenuSoldOutResponse from(MenuResult result) {
        return MenuSoldOutResponse.builder()
                .menuId(result.getMenuId())
                .isSoldOut(result.isSoldOut())
                .updatedAt(result.getUpdatedAt())
                .build();
    }
}
