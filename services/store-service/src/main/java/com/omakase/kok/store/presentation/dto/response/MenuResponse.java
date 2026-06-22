package com.omakase.kok.store.presentation.dto.response;

import com.omakase.kok.store.application.result.MenuResult;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class MenuResponse {

    private UUID menuId;
    private String name;
    private int price;
    private String description;
    private boolean isSoldOut;
    private String thumbnailUrl;
    private int displayOrder;

    public static MenuResponse from(MenuResult result) {
        return MenuResponse.builder()
                .menuId(result.getMenuId())
                .name(result.getName())
                .price(result.getPrice())
                .description(result.getDescription())
                .isSoldOut(result.isSoldOut())
                .thumbnailUrl(result.getThumbnailUrl())
                .displayOrder(result.getDisplayOrder())
                .build();
    }
}
