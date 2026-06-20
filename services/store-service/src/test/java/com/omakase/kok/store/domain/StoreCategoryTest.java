package com.omakase.kok.store.domain;

import com.omakase.kok.store.domain.entity.StoreCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoreCategoryTest {

    @Test
    @DisplayName("parent가 없으면 1뎁스(대분류)")
    void root_category_is_not_sub() {
        StoreCategory root = StoreCategory.create("한식", 1, null);
        assertThat(root.isSubCategory()).isFalse();
    }

    @Test
    @DisplayName("parent가 있으면 2뎁스(소분류)")
    void child_category_is_sub() {
        StoreCategory root = StoreCategory.create("한식", 1, null);
        StoreCategory child = StoreCategory.create("국밥", 1, root);
        assertThat(child.isSubCategory()).isTrue();
    }

    @Test
    @DisplayName("update() null 필드는 기존 값 유지")
    void update_null_preserves_existing() {
        StoreCategory category = StoreCategory.create("한식", 1, null);

        category.update(null, null);

        assertThat(category.getName()).isEqualTo("한식");
        assertThat(category.getSortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("update() 이름과 정렬순서 변경")
    void update_changes_fields() {
        StoreCategory category = StoreCategory.create("한식", 1, null);

        category.update("중식", 2);

        assertThat(category.getName()).isEqualTo("중식");
        assertThat(category.getSortOrder()).isEqualTo(2);
    }
}
