package com.omakase.kok.store.application.result;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.omakase.kok.store.domain.entity.StoreAmenity;
import com.omakase.kok.store.domain.enums.AmenityType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
// @JsonDeserialize: Builder만 있으면 Jackson이 기본 생성자 없이 역직렬화 불가. Redis 캐시 복원 시 필요
@JsonDeserialize(builder = StoreAmenityResult.StoreAmenityResultBuilder.class)
public class StoreAmenityResult {

    private UUID amenityId;
    private AmenityType amenityType;

    public static StoreAmenityResult from(StoreAmenity amenity) {
        return StoreAmenityResult.builder()
            .amenityId(amenity.getAmenityId())
            .amenityType(amenity.getAmenityType())
            .build();
    }

    @Getter
    @Builder
    public static class Bulk {
        private UUID storeId;
        private List<StoreAmenityResult> amenities;
    }
}
