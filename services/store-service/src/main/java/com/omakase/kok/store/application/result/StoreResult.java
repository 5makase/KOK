package com.omakase.kok.store.application.result;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.enums.StoreStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class StoreResult {

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

    public static StoreResult from(Store store) {
        return StoreResult.builder()
                .storeId(store.getStoreId())
                .ownerId(store.getOwnerId())
                .categoryId(store.getCategory().getCategoryId())
                .categoryName(store.getCategory().getName())
                .name(store.getName())
                .phone(store.getPhone())
                .addressSido(store.getAddressSido())
                .addressSigungu(store.getAddressSigungu())
                .addressDong(store.getAddressDong())
                .addressDetail(store.getAddressDetail())
                .latitude(store.getLatitude())
                .longitude(store.getLongitude())
                .description(store.getDescription())
                .status(store.getStatus())
                .maxCapacity(store.getMaxCapacity())
                .averageRating(store.getAverageRating())
                .reviewCount(store.getReviewCount())
                .thumbnailUrl(store.getThumbnailUrl())
                .build();
    }
}
