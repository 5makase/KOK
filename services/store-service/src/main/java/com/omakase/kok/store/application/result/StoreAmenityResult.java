package com.omakase.kok.store.application.result;

import com.omakase.kok.store.domain.entity.StoreAmenity;
import com.omakase.kok.store.domain.enums.AmenityType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
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
