package com.omakase.kok.store.application.result;

import com.omakase.kok.store.domain.entity.StoreAmenity;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@Jacksonized // Lombok Builder와 Jackson 역직렬화 연동(Redis 캐시 복원 시 필요)
public class StoreAmenityResult {

    private UUID amenityId;
    private String amenityType; // enum → String 변환 (Presentation 계층에 도메인 타입 노출 방지)

    public static StoreAmenityResult from(StoreAmenity amenity) {
        return StoreAmenityResult.builder()
            .amenityId(amenity.getAmenityId())
            .amenityType(amenity.getAmenityType().name())
            .build();
    }

    @Getter
    @Builder
    public static class Bulk {
        private UUID storeId;
        private List<StoreAmenityResult> amenities;
    }
}
