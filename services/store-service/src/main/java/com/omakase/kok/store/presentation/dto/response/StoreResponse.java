package com.omakase.kok.store.presentation.dto.response;

import com.omakase.kok.store.application.result.StoreAmenityResult;
import com.omakase.kok.store.application.result.StoreHoursResult;
import com.omakase.kok.store.application.result.StoreImageResult;
import com.omakase.kok.store.application.result.StoreResult;
import com.omakase.kok.store.application.result.StoreResult.CategoryInfo;
import com.omakase.kok.store.application.result.StoreResult.MenuPreviewResult;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class StoreResponse {

    private UUID storeId;
    private UUID ownerId;
    private CategoryInfo category;
    private String name;
    private String phone;
    private String addressSido;
    private String addressSigungu;
    private String addressDong;
    private String addressDetail;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String description;
    private String status;
    private Integer maxCapacity;
    private BigDecimal averageRating;
    private int reviewCount;
    private String thumbnailUrl;
    private LocalDateTime createdAt;

    // 상세 조회 - 목록 조회 시 null
    private StoreHoursResult todayHours;
    private List<StoreAmenityResult> amenities;
    private List<StoreImageResult> imagePreview;
    private List<MenuPreviewResult> menuPreview;

    public static StoreResponse from(StoreResult result) {
        return StoreResponse.builder()
                .storeId(result.getStoreId())
                .ownerId(result.getOwnerId())
                .category(result.getCategory())
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
                .createdAt(result.getCreatedAt())
                .todayHours(result.getTodayHours())
                .amenities(result.getAmenities())
                .imagePreview(result.getImagePreview())
                .menuPreview(result.getMenuPreview())
                .build();
    }
}
