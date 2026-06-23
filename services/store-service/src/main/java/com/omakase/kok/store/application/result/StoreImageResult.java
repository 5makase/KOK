package com.omakase.kok.store.application.result;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.omakase.kok.store.domain.entity.StoreImage;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
// @JsonDeserialize: Builder만 있으면 Jackson이 기본 생성자 없이 역직렬화 불가. Redis 캐시 복원 시 필요
@JsonDeserialize(builder = StoreImageResult.StoreImageResultBuilder.class)
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
