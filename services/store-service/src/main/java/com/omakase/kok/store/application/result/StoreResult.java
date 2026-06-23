package com.omakase.kok.store.application.result;

import com.omakase.kok.store.domain.entity.Menu;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreHours;
import com.omakase.kok.store.domain.entity.StoreImage;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
// @JsonDeserialize: Builder만 있으면 Jackson이 기본 생성자 없이 역직렬화 불가. Redis 캐시 복원 시 필요
@JsonDeserialize(builder = StoreResult.StoreResultBuilder.class)
public class StoreResult {

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
    private String status; // enum → String 변환 (Presentation 계층에 도메인 타입 노출 방지)
    private Integer maxCapacity;
    private BigDecimal averageRating;
    private int reviewCount;
    private String thumbnailUrl;
    private LocalDateTime createdAt;

    // 매장 상세 홈 탭 전용 - getStore() 호출 시에만 주입, 목록 조회 시 null
    private StoreHoursResult todayHours;
    private List<StoreAmenityResult> amenities;
    private List<StoreImageResult> imagePreview;
    private List<MenuPreviewResult> menuPreview;

    @Getter
    @Builder
    @JsonDeserialize(builder = StoreResult.CategoryInfo.CategoryInfoBuilder.class)
    public static class CategoryInfo {
        private UUID categoryId;
        private String categoryName;
        // 1뎁스(대분류)이면 null, 2뎁스(소분류)이면 상위 카테고리명
        private String parentCategoryName;
    }

    @Getter
    @Builder
    @JsonDeserialize(builder = StoreResult.MenuPreviewResult.MenuPreviewResultBuilder.class)
    public static class MenuPreviewResult {
        private UUID menuId;
        private String name;
        private int price;
        private String thumbnailUrl;

        public static MenuPreviewResult from(Menu menu) {
            return MenuPreviewResult.builder()
                    .menuId(menu.getMenuId())
                    .name(menu.getName())
                    .price(menu.getPrice())
                    .thumbnailUrl(menu.getThumbnailUrl())
                    .build();
        }
    }

    // 목록 조회용 - 서브 데이터(todayHours/amenities 등) 없이 기본 정보만 반환
    public static StoreResult from(Store store) {
        return StoreResult.builder()
                .storeId(store.getStoreId())
                .ownerId(store.getOwnerId())
                .category(CategoryInfo.builder()
                        .categoryId(store.getCategory().getCategoryId())
                        .categoryName(store.getCategory().getName())
                        .parentCategoryName(store.getCategory().isSubCategory()
                                ? store.getCategory().getParent().getName()
                                : null)
                        .build())
                .name(store.getName())
                .phone(store.getPhone())
                .addressSido(store.getAddress().getSido())
                .addressSigungu(store.getAddress().getSigungu())
                .addressDong(store.getAddress().getDong())
                .addressDetail(store.getAddress().getDetail())
                .latitude(store.getAddress().getLatitude())
                .longitude(store.getAddress().getLongitude())
                .description(store.getDescription())
                .status(store.getStatus().name())
                .maxCapacity(store.getMaxCapacity())
                .averageRating(store.getAverageRating())
                .reviewCount(store.getReviewCount())
                .thumbnailUrl(store.getThumbnailUrl())
                .createdAt(store.getCreatedAt())
                .build();
    }

    // 상세 조회용 - 매장 서브 데이터 포함
    public static StoreResult of(Store store, StoreHours todayHours,
                                 List<StoreAmenityResult> amenities,
                                 List<StoreImage> imagePreview,
                                 List<Menu> menuPreview) {
        return from(store).toBuilder()
                .todayHours(todayHours != null ? StoreHoursResult.from(todayHours) : null)
                .amenities(amenities)
                .imagePreview(imagePreview.stream().map(StoreImageResult::from).toList())
                .menuPreview(menuPreview.stream().map(MenuPreviewResult::from).toList())
                .build();
    }
}
