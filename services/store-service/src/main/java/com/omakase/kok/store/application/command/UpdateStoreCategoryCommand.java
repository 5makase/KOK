package com.omakase.kok.store.application.command;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UpdateStoreCategoryCommand {

    private UUID categoryId;
    private String name;
    private int sortOrder;
}
