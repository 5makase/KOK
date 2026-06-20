package com.omakase.kok.store.presentation.dto.response;

import com.omakase.kok.store.application.result.MenuResult;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class MenuDeleteResponse {

    private UUID menuId;
    private LocalDateTime deletedAt;

    public static MenuDeleteResponse from(MenuResult result) {
        return MenuDeleteResponse.builder()
                .menuId(result.getMenuId())
                .deletedAt(result.getDeletedAt())
                .build();
    }
}
