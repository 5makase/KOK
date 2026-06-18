package com.omakase.kok.store.application.result;

import com.omakase.kok.store.domain.entity.Menu;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class MenuResult {

    private UUID menuId;
    private String name;
    private int price;
    private String description;
    private boolean isSoldOut;
    private String thumbnailUrl;
    private int displayOrder;

    public static MenuResult from(Menu menu) {
        return MenuResult.builder()
                .menuId(menu.getMenuId())
                .name(menu.getName())
                .price(menu.getPrice())
                .description(menu.getDescription())
                .isSoldOut(menu.isSoldOut())
                .thumbnailUrl(menu.getThumbnailUrl())
                .displayOrder(menu.getDisplayOrder())
                .build();
    }
}
