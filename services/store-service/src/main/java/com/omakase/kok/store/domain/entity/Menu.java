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
@Table(name = "p_menus")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "menu_id")
    private UUID menuId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "price", nullable = false)
    private int price;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_sold_out", nullable = false)
    private boolean isSoldOut;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    // 메뉴 최초 등록 (품절 여부 false로 초기화)
    public static Menu create(Store store, String name, int price, String description,
                              String thumbnailUrl, int displayOrder) {
        Menu menu = new Menu();
        menu.store = store;
        menu.name = name;
        menu.price = price;
        menu.description = description;
        menu.thumbnailUrl = thumbnailUrl;
        menu.displayOrder = displayOrder;
        menu.isSoldOut = false;
        return menu;
    }

    // 메뉴 정보 수정 - null이면 기존 값 유지
    public void update(String name, Integer price, String description,
                       String thumbnailUrl, Integer displayOrder) {
        if (name != null) this.name = name;
        if (price != null) this.price = price;
        if (description != null) this.description = description;
        if (thumbnailUrl != null) this.thumbnailUrl = thumbnailUrl;
        if (displayOrder != null) this.displayOrder = displayOrder;
    }

    // 품절 처리
    public void soldOut() {
        this.isSoldOut = true;
    }

    // 판매 재개
    public void onSale() {
        this.isSoldOut = false;
    }
}
