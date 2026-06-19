package com.omakase.kok.store.application.command;

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
}
