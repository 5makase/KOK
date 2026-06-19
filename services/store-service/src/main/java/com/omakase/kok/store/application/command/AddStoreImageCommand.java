package com.omakase.kok.store.application.command;

import com.omakase.kok.store.presentation.dto.request.AddStoreImageRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AddStoreImageCommand {

    private UUID storeId;
    private UUID requesterId;
    private List<ImageEntry> images;

    @Getter
    @Builder
    public static class ImageEntry {
        private String imageUrl;
        private int displayOrder;
    }

    public static AddStoreImageCommand of(UUID storeId, UUID requesterId, AddStoreImageRequest request) {
        List<ImageEntry> entries = request.getImages().stream()
                .map(e -> ImageEntry.builder()
                        .imageUrl(e.getImageUrl())
                        .displayOrder(e.getDisplayOrder())
                        .build())
                .toList();

        return AddStoreImageCommand.builder()
                .storeId(storeId)
                .requesterId(requesterId)
                .images(entries)
                .build();
    }
}
