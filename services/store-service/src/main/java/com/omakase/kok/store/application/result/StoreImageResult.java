package com.omakase.kok.store.application.result;

import com.omakase.kok.store.domain.entity.StoreImage;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.UUID;

@Getter
@Builder
@Jacksonized // Lombok Builder와 Jackson 역직렬화 연동(Redis 캐시 복원 시 필요)
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
