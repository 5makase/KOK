package com.omakase.kok.store.application.command;

import com.omakase.kok.store.presentation.dto.request.CreateMenuRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CreateMenuCommand {

    private UUID storeId;
    private UUID requesterId;
    private String role;
    private String name;
    private int price;
    private String description;
    private String thumbnailUrl;
    private int displayOrder;

    public static CreateMenuCommand of(UUID storeId, UUID requesterId, String role, CreateMenuRequest request) {
        return CreateMenuCommand.builder()
                .storeId(storeId)
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
