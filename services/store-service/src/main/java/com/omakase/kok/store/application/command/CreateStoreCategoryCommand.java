package com.omakase.kok.store.application.command;

import com.omakase.kok.store.presentation.dto.request.CreateStoreCategoryRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CreateStoreCategoryCommand {

    private String name;
    private int sortOrder;
    private UUID parentId;

    public static CreateStoreCategoryCommand from(CreateStoreCategoryRequest request) {
        return CreateStoreCategoryCommand.builder()
                .name(request.getName())
                .sortOrder(request.getSortOrder())
                .parentId(request.getParentId())
                .build();
    }
}
