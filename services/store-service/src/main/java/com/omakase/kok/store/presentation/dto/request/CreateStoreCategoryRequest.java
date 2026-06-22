package com.omakase.kok.store.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.UUID;

@Getter
public class CreateStoreCategoryRequest {

    @NotBlank
    private String name;

    @NotNull
    private Integer sortOrder;

    private UUID parentId; // null이면 1뎁스 루트 카테고리
}
