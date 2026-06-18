package com.omakase.kok.store.application.result;

import com.omakase.kok.store.domain.entity.StoreCategory;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class StoreCategoryResult {

    private UUID categoryId;
    private String name;
    private int sortOrder;
    private List<StoreCategoryResult> children;

    public static StoreCategoryResult from(StoreCategory category) {
        return StoreCategoryResult.builder()
                .categoryId(category.getCategoryId())
                .name(category.getName())
                .sortOrder(category.getSortOrder())
                .children(category.getChildren().stream()
                        .map(StoreCategoryResult::from)
                        .toList())
                .build();
    }
}
