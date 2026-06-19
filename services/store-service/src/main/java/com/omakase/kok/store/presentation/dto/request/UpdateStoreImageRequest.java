package com.omakase.kok.store.presentation.dto.request;

import lombok.Getter;

@Getter
public class UpdateStoreImageRequest {

    private String imageUrl;

    private Integer displayOrder;
}
