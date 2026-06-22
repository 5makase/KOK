package com.omakase.kok.store.presentation.dto.request;

import lombok.Getter;

@Getter
public class UpdateStoreCategoryRequest {

    private String name;

    private Integer sortOrder;
}
