package com.omakase.kok.store.domain;

import com.omakase.kok.store.domain.entity.Menu;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreCategory;
import com.omakase.kok.store.domain.vo.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MenuTest {

    private Store store;

    @BeforeEach
    void setUp() {
        UUID ownerId = UUID.randomUUID();
        StoreCategory category = StoreCategory.create("한식", 1, null);
        store = Store.create(ownerId, category, "테스트 매장", null,
                new Address("서울", "강남", null, null, null, null), null, null);
    }

    @Test
    @DisplayName("메뉴 생성 시 품절 여부는 false로 초기화")
    void create_initial_sold_out_is_false() {
        Menu menu = Menu.create(store, "된장찌개", 8000, "구수한 된장찌개", null, 1);

        assertThat(menu.isSoldOut()).isFalse();
        assertThat(menu.getName()).isEqualTo("된장찌개");
        assertThat(menu.getPrice()).isEqualTo(8000);
        assertThat(menu.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("update - null 필드는 기존 값 유지")
    void update_null_preserves_existing() {
        Menu menu = Menu.create(store, "된장찌개", 8000, "설명", "thumb.jpg", 1);

        menu.update(null, null, null, null, null);

        assertThat(menu.getName()).isEqualTo("된장찌개");
        assertThat(menu.getPrice()).isEqualTo(8000);
        assertThat(menu.getDescription()).isEqualTo("설명");
        assertThat(menu.getThumbnailUrl()).isEqualTo("thumb.jpg");
        assertThat(menu.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("update - 전송된 필드만 반영")
    void update_only_provided_fields() {
        Menu menu = Menu.create(store, "된장찌개", 8000, "설명", null, 1);

        menu.update("김치찌개", 9000, null, null, 2);

        assertThat(menu.getName()).isEqualTo("김치찌개");
        assertThat(menu.getPrice()).isEqualTo(9000);
        assertThat(menu.getDescription()).isEqualTo("설명"); // null이므로 기존 유지
        assertThat(menu.getDisplayOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("soldOut - 품절 처리")
    void soldOut_sets_flag_true() {
        Menu menu = Menu.create(store, "된장찌개", 8000, null, null, 1);
        assertThat(menu.isSoldOut()).isFalse();

        menu.soldOut();

        assertThat(menu.isSoldOut()).isTrue();
    }

    @Test
    @DisplayName("onSale - 판매 재개")
    void onSale_sets_flag_false() {
        Menu menu = Menu.create(store, "된장찌개", 8000, null, null, 1);
        menu.soldOut();
        assertThat(menu.isSoldOut()).isTrue();

        menu.onSale();

        assertThat(menu.isSoldOut()).isFalse();
    }
}
