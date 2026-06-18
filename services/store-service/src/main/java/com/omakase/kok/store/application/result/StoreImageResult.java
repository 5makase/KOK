package com.omakase.kok.store.application.result;

import com.omakase.kok.store.domain.entity.StoreImage;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class StoreImageResult {

    private UUID imageId;
    private String imageUrl;
    private int displayOrder;

    public static StoreImageResult from(StoreImage image) {
        return StoreImageResult.builder()
                .imageId(image.getImageId())
                .imageUrl(image.getImageUrl())
                .displayOrder(image.getDisplayOrder())
                .build();
    }
}
