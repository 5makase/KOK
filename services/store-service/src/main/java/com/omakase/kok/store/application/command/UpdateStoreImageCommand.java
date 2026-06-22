package com.omakase.kok.store.application.command;

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
}
