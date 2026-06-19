package com.omakase.kok.store.application.command;

import com.omakase.kok.store.presentation.dto.request.UpdateStoreImageRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UpdateStoreImageCommand {

    private UUID storeId;
    private UUID imageId;
    private UUID requesterId;
    private String imageUrl;
    private Integer displayOrder;

    public static UpdateStoreImageCommand of(UUID storeId, UUID imageId, UUID requesterId, UpdateStoreImageRequest request) {
        return UpdateStoreImageCommand.builder()
                .storeId(storeId)
                .imageId(imageId)
                .requesterId(requesterId)
                .imageUrl(request.getImageUrl())
                .displayOrder(request.getDisplayOrder())
                .build();
    }
}
