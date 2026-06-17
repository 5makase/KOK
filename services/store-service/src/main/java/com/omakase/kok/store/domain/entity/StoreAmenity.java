package com.omakase.kok.store.domain.entity;

import com.omakase.kok.common.entity.BaseEntity;
import com.omakase.kok.store.domain.enums.AmenityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "p_store_amenities",
        uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "amenity_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreAmenity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "amenity_id")
    private UUID amenityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "amenity_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private AmenityType amenityType;

    // 편의시설 등록
    public static StoreAmenity create(Store store, AmenityType amenityType) {
        StoreAmenity amenity = new StoreAmenity();
        amenity.store = store;
        amenity.amenityType = amenityType;
        return amenity;
    }

    // soft delete된 편의시설 재활성화 (UniqueConstraint 충돌 방지)
    public void restore() {
        clearDeleted();
    }
}
