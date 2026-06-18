package com.omakase.kok.store.presentation.dto.response;

import com.omakase.kok.store.application.result.StoreAmenityResult;
import com.omakase.kok.store.domain.enums.AmenityType;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class StoreAmenityResponse {

    private UUID amenityId;
    private AmenityType amenityType;

    public static StoreAmenityResponse from(StoreAmenityResult result) {
        return StoreAmenityResponse.builder()
                .amenityId(result.getAmenityId())
                .amenityType(result.getAmenityType())
                .build();
    }
}
