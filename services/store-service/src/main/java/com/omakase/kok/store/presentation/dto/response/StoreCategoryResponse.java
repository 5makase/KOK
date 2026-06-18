package com.omakase.kok.store.presentation.dto.response;

import com.omakase.kok.store.application.result.StoreCategoryResult;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class StoreCategoryResponse {

    private UUID categoryId;
    private String name;
    private int sortOrder;
    private List<StoreCategoryResponse> children;

    public static StoreCategoryResponse from(StoreCategoryResult result) {
        return StoreCategoryResponse.builder()
                .categoryId(result.getCategoryId())
                .name(result.getName())
                .sortOrder(result.getSortOrder())
                .children(result.getChildren().stream()
                        .map(StoreCategoryResponse::from)
                        .toList())
                .build();
    }
}
