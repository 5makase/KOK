package com.omakase.kok.store.presentation.dto.response;

import com.omakase.kok.store.application.result.MenuResult;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class MenuUpdateResponse {

    private UUID menuId;
    private String name;
    private int price;
    private String description;
    private String thumbnailUrl;
    private int displayOrder;
    private LocalDateTime updatedAt;

    public static MenuUpdateResponse from(MenuResult result) {
        return MenuUpdateResponse.builder()
                .menuId(result.getMenuId())
                .name(result.getName())
                .price(result.getPrice())
                .description(result.getDescription())
                .thumbnailUrl(result.getThumbnailUrl())
                .displayOrder(result.getDisplayOrder())
                .updatedAt(result.getUpdatedAt())
                .build();
    }
}
