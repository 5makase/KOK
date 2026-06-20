package com.omakase.kok.store.application.command;

import com.omakase.kok.store.presentation.dto.request.UpdateMenuRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UpdateMenuCommand {

    private UUID menuId;
    private UUID requesterId;
    private String name;
    private int price;
    private String description;
    private String thumbnailUrl;
    private int displayOrder;

    public static UpdateMenuCommand of(UUID menuId, UUID requesterId, UpdateMenuRequest request) {
        return UpdateMenuCommand.builder()
                .menuId(menuId)
                .requesterId(requesterId)
                .name(request.getName())
                .price(request.getPrice())
                .description(request.getDescription())
                .thumbnailUrl(request.getThumbnailUrl())
                .displayOrder(request.getDisplayOrder())
                .build();
    }
}
