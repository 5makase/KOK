package com.omakase.kok.store.application.command;

import com.omakase.kok.store.presentation.dto.request.UpdateMenuRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UpdateMenuCommand {

    private UUID storeId;
    private UUID menuId;
    private UUID requesterId;
    private String role;
    private String name;
    private Integer price;
    private String description;
    private String thumbnailUrl;
    private Integer displayOrder;

    public static UpdateMenuCommand of(UUID storeId, UUID menuId, UUID requesterId, String role, UpdateMenuRequest request) {
        return UpdateMenuCommand.builder()
                .storeId(storeId)
                .menuId(menuId)
                .requesterId(requesterId)
                .role(role)
                .name(request.getName())
                .price(request.getPrice())
                .description(request.getDescription())
                .thumbnailUrl(request.getThumbnailUrl())
                .displayOrder(request.getDisplayOrder())
                .build();
    }
}
