package com.omakase.kok.store.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class UpdateStoreCategoryRequest {

    @NotBlank
    private String name;

    @NotNull
    private Integer sortOrder;
}
