package com.omakase.kok.store.presentation.dto.response;

import com.omakase.kok.store.application.result.MenuResult;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class MenuCreateResponse {

    private UUID menuId;
    private String name;
    private int price;
    private boolean isSoldOut;

    public static MenuCreateResponse from(MenuResult result) {
        return MenuCreateResponse.builder()
                .menuId(result.getMenuId())
                .name(result.getName())
                .price(result.getPrice())
                .isSoldOut(result.isSoldOut())
                .build();
    }
}
