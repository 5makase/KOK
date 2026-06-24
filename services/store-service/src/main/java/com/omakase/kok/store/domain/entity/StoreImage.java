package com.omakase.kok.store.domain.entity;

import com.omakase.kok.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "p_store_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "image_id")
    private UUID imageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    // 이미지 등록
    public static StoreImage create(Store store, String imageUrl, int displayOrder) {
        StoreImage image = new StoreImage();
        image.store = store;
        image.imageUrl = imageUrl;
        image.displayOrder = displayOrder;
        return image;
    }

    // 이미지 URL 및 정렬순서 수정
    public void update(String imageUrl, int displayOrder) {
        this.imageUrl = imageUrl;
        this.displayOrder = displayOrder;
    }

    // soft delete된 이미지 재활성화 - display_order를 유지한 채 deleted_at만 제거
    // partial index(deleted_at IS NULL)가 활성 슬롯 유일성을 보장하므로 insert 없이 재사용 가능
    public void restore() {
        clearDeleted();
    }
}
