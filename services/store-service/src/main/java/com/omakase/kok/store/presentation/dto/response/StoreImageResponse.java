package com.omakase.kok.store.presentation.dto.response;

import com.omakase.kok.store.application.result.StoreImageResult;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class StoreImageResponse {

    private UUID imageId;
    private String imageUrl;
    private int displayOrder;

    public static StoreImageResponse from(StoreImageResult result) {
        return StoreImageResponse.builder()
                .imageId(result.getImageId())
                .imageUrl(result.getImageUrl())
                .displayOrder(result.getDisplayOrder())
                .build();
    }
}
