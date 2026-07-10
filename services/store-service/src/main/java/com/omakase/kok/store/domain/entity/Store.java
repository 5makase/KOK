package com.omakase.kok.store.domain.entity;

import com.omakase.kok.common.entity.BaseEntity;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.domain.enums.StoreStatus;
import com.omakase.kok.store.domain.vo.Address;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "p_stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store extends BaseEntity {

    // OPEN 전환 시 요구되는 영업시간 등록 개수(요일 수)
    private static final int REQUIRED_HOURS_FOR_OPEN = DayOfWeek.values().length;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private StoreCategory category;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "phone", length = 20)
    private String phone;

    @Embedded
    private Address address;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private StoreStatus status;

    @Column(name = "max_capacity")
    private Integer maxCapacity;

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @OneToMany(mappedBy = "store", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<StoreHours> storeHours = new ArrayList<>();

    @OneToMany(mappedBy = "store", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<StoreAmenity> amenities = new ArrayList<>();

    @OneToMany(mappedBy = "store", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<Menu> menus = new ArrayList<>();

    @OneToMany(mappedBy = "store", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<StoreImage> images = new ArrayList<>();

    // 가게 최초 등록 (상태 PREPARING, 평점 0으로 초기화)
    public static Store create(UUID ownerId, StoreCategory category, String name, String phone,
                               Address address, String description, Integer maxCapacity) {
        Store store = new Store();
        store.ownerId = ownerId;
        store.category = category;
        store.name = name;
        store.phone = phone;
        store.address = address;
        store.description = description;
        store.maxCapacity = maxCapacity;
        store.status = StoreStatus.PREPARING;
        store.averageRating = BigDecimal.ZERO;
        store.reviewCount = 0;
        return store;
    }

    // 가게 기본 정보 수정 - null 필드는 기존 값 유지 (부분 수정 지원)
    public void update(String name, String phone, Address address,
                       String description, Integer maxCapacity, StoreCategory category) {
        if (name != null) this.name = name;
        if (phone != null) this.phone = phone;
        if (address != null) this.address = address;
        if (description != null) this.description = description;
        if (maxCapacity != null) this.maxCapacity = maxCapacity;
        if (category != null) this.category = category;
    }

    /**
     * 상태 전이 유효성 검증 후 변경
     * OPEN 전환은 영업시간이 7일치(요일 수) 모두 등록돼 있어야 함
     * PERMANENTLY_CLOSED 전이 시 soft delete 동시 처리
     * findActiveStore(deletedAt IS NULL) 조건에서 폐업 매장이 자동으로 제외되도록 보장
     */
    public void changeStatus(StoreStatus next, int registeredHoursCount, UUID userId) {
        if (next == StoreStatus.OPEN && registeredHoursCount < REQUIRED_HOURS_FOR_OPEN) {
            throw new BaseException(StoreErrorCode.STORE_HOURS_REQUIRED_FOR_OPEN);
        }

        this.status.validateTransitionTo(next);
        this.status = next;
        if (next == StoreStatus.PERMANENTLY_CLOSED) {
            this.delete(userId);
        }
    }

    // 리뷰 서비스 이벤트 수신 시 평점 갱신
    public void updateRating(BigDecimal newAverageRating, Integer newReviewCount) {
        this.averageRating = newAverageRating.setScale(2, RoundingMode.HALF_UP);
        this.reviewCount = newReviewCount;
    }

    // 대표 이미지 URL 변경
    public void updateThumbnail(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    // 요청자가 가게 소유자인지 확인
    public boolean isOwnedBy(UUID userId) {
        return this.ownerId.equals(userId);
    }

    // 예약/웨이팅 가능 상태인지 확인
    public boolean isAvailableForService() {
        return this.status == StoreStatus.OPEN;
    }

    // 랭킹에 노출될 자격이 있는지 확인 (삭제되지 않았고, 영업 중이며, 리뷰가 하나 이상 있어야 함)
    public boolean isRankable() {
        return !isDeleted() && isAvailableForService() && reviewCount > 0;
    }
}
