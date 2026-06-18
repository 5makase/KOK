package com.omakase.kok.store.presentation.dto.response;

import com.omakase.kok.store.application.result.StoreResult;
import com.omakase.kok.store.domain.enums.StoreStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class StoreResponse {

    private UUID storeId;
    private UUID ownerId;
    private UUID categoryId;
    private String categoryName;
    private String name;
    private String phone;
    private String addressSido;
    private String addressSigungu;
    private String addressDong;
    private String addressDetail;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String description;
    private StoreStatus status;
    private Integer maxCapacity;
    private BigDecimal averageRating;
    private int reviewCount;
    private String thumbnailUrl;

    public static StoreResponse from(StoreResult result) {
        return StoreResponse.builder()
                .storeId(result.getStoreId())
                .ownerId(result.getOwnerId())
                .categoryId(result.getCategoryId())
                .categoryName(result.getCategoryName())
                .name(result.getName())
                .phone(result.getPhone())
                .addressSido(result.getAddressSido())
                .addressSigungu(result.getAddressSigungu())
                .addressDong(result.getAddressDong())
                .addressDetail(result.getAddressDetail())
                .latitude(result.getLatitude())
                .longitude(result.getLongitude())
                .description(result.getDescription())
                .status(result.getStatus())
                .maxCapacity(result.getMaxCapacity())
                .averageRating(result.getAverageRating())
                .reviewCount(result.getReviewCount())
                .thumbnailUrl(result.getThumbnailUrl())
                .build();
    }
}
