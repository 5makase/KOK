package com.omakase.kok.store.domain;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreCategory;
import com.omakase.kok.store.domain.enums.StoreStatus;
import com.omakase.kok.store.domain.vo.Address;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoreTest {

    private StoreCategory category;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        category = StoreCategory.create("한식", 1, null);
        ownerId = UUID.randomUUID();
    }

    @Test
    @DisplayName("매장 생성 시 상태는 PREPARING, 평점은 0")
    void create_initial_state() {
        Store store = Store.create(ownerId, category, "테스트 매장", "02-0000-0000",
                address(), "설명", 50);

        assertThat(store.getStatus()).isEqualTo(StoreStatus.PREPARING);
        assertThat(store.getAverageRating()).isEqualByComparingTo("0");
        assertThat(store.getReviewCount()).isZero();
    }

    @Test
    @DisplayName("update() null 필드는 기존 값 유지")
    void update_null_preserves_existing() {
        Store store = Store.create(ownerId, category, "원래 이름", null, address(), null, 30);

        store.update(null, null, null, null, null, null);

        assertThat(store.getName()).isEqualTo("원래 이름");
        assertThat(store.getMaxCapacity()).isEqualTo(30);
        assertThat(store.getCategory()).isSameAs(category);
    }

    @Test
    @DisplayName("update() category null이면 기존 카테고리 유지")
    void update_category_null_keeps_original() {
        Store store = Store.create(ownerId, category, "이름", null, address(), null, null);
        StoreCategory newCategory = StoreCategory.create("중식", 2, null);

        store.update(null, null, null, null, null, null);
        assertThat(store.getCategory()).isSameAs(category);

        store.update(null, null, null, null, null, newCategory);
        assertThat(store.getCategory()).isSameAs(newCategory);
    }

    @Test
    @DisplayName("changeStatus(PERMANENTLY_CLOSED) 시 soft delete 동시 처리")
    void change_status_permanently_closed_deletes() {
        Store store = Store.create(ownerId, category, "이름", null, address(), null, null);
        store.changeStatus(StoreStatus.OPEN, ownerId); // PREPARING → OPEN

        store.changeStatus(StoreStatus.PERMANENTLY_CLOSED, ownerId);

        assertThat(store.getStatus()).isEqualTo(StoreStatus.PERMANENTLY_CLOSED);
        assertThat(store.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("허용되지 않는 상태 전이는 예외")
    void change_status_invalid_transition_throws() {
        Store store = Store.create(ownerId, category, "이름", null, address(), null, null);
        // PREPARING 상태에서 CLOSED 전이 불가
        assertThatThrownBy(() -> store.changeStatus(StoreStatus.CLOSED, ownerId))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.INVALID_STORE_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("isOwnedBy() — 본인이면 true, 타인이면 false")
    void is_owned_by() {
        Store store = Store.create(ownerId, category, "이름", null, address(), null, null);
        UUID other = UUID.randomUUID();

        assertThat(store.isOwnedBy(ownerId)).isTrue();
        assertThat(store.isOwnedBy(other)).isFalse();
    }

    @Test
    @DisplayName("updateRating() - 평점과 리뷰수 갱신")
    void update_rating_updates_fields() {
        Store store = Store.create(ownerId, category, "이름", null, address(), null, null);

        store.updateRating(new BigDecimal("4.50"), 10);

        assertThat(store.getAverageRating()).isEqualByComparingTo("4.50");
        assertThat(store.getReviewCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("updateThumbnail() - 썸네일 URL 갱신")
    void update_thumbnail_sets_url() {
        Store store = Store.create(ownerId, category, "이름", null, address(), null, null);

        store.updateThumbnail("https://cdn.example.com/thumb.jpg");

        assertThat(store.getThumbnailUrl()).isEqualTo("https://cdn.example.com/thumb.jpg");
    }

    @Test
    @DisplayName("isAvailableForService() - OPEN이면 true, 그 외 false")
    void is_available_for_service() {
        Store store = Store.create(ownerId, category, "이름", null, address(), null, null);

        assertThat(store.isAvailableForService()).isFalse(); // PREPARING

        store.changeStatus(StoreStatus.OPEN, ownerId);
        assertThat(store.isAvailableForService()).isTrue();
    }

    private Address address() {
        return new Address("서울특별시", "강남구", "테헤란로 123", null, null, null);
    }
}
