package com.omakase.kok.store.application.command;

import com.omakase.kok.store.presentation.dto.request.AddStoreImageRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AddStoreImageCommand {

    private UUID storeId;
    private UUID requesterId;
    private String imageUrl;
    private int displayOrder;

    public static AddStoreImageCommand of(UUID storeId, UUID requesterId, AddStoreImageRequest request) {
        return AddStoreImageCommand.builder()
                .storeId(storeId)
                .requesterId(requesterId)
                .imageUrl(request.getImageUrl())
                .displayOrder(request.getDisplayOrder())
                .build();
    }
}
