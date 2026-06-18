package com.omakase.kok.store.application.command;

import com.omakase.kok.store.presentation.dto.request.UpdateStoreImageRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UpdateStoreImageCommand {

    private UUID imageId;
    private UUID requesterId;
    private String imageUrl;
    private int displayOrder;

    public static UpdateStoreImageCommand of(UUID imageId, UUID requesterId, UpdateStoreImageRequest request) {
        return UpdateStoreImageCommand.builder()
                .imageId(imageId)
                .requesterId(requesterId)
                .imageUrl(request.getImageUrl())
                .displayOrder(request.getDisplayOrder())
                .build();
    }
}
