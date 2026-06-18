package com.omakase.kok.store.application.command;

import com.omakase.kok.store.presentation.dto.request.UpdateStoreCategoryRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UpdateStoreCategoryCommand {

    private UUID categoryId;
    private String name;
    private int sortOrder;

    public static UpdateStoreCategoryCommand of(UUID categoryId, UpdateStoreCategoryRequest request) {
        return UpdateStoreCategoryCommand.builder()
                .categoryId(categoryId)
                .name(request.getName())
                .sortOrder(request.getSortOrder())
                .build();
    }
}
