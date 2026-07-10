package com.omakase.kok.store.application;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.application.cache.StoreListCacheRepository;
import com.omakase.kok.store.application.command.CreateStoreCategoryCommand;
import com.omakase.kok.store.application.command.UpdateStoreCategoryCommand;
import com.omakase.kok.store.application.result.StoreCategoryResult;
import com.omakase.kok.store.domain.entity.StoreCategory;
import com.omakase.kok.store.domain.repository.StoreCategoryRepository;
import com.omakase.kok.store.domain.repository.StoreRepository;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreCategoryServiceTest {

    @Mock StoreCategoryRepository storeCategoryRepository;
    @Mock StoreRepository storeRepository;
    @Mock StoreListCacheRepository storeListCacheRepository;

    @InjectMocks
    StoreCategoryService storeCategoryService;

    // createCategory

    @Test
    @DisplayName("대분류 카테고리 생성 성공")
    void createCategory_root_success() {
        CreateStoreCategoryCommand command = CreateStoreCategoryCommand.builder()
                .name("한식").sortOrder(1).parentId(null).build();

        when(storeCategoryRepository.existsActiveSiblingByName(eq("한식"), isNull(), isNull())).thenReturn(false);
        when(storeCategoryRepository.existsActiveSiblingBySortOrder(eq(1), isNull(), isNull())).thenReturn(false);
        when(storeCategoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StoreCategoryResult result = storeCategoryService.createCategory(command);

        assertThat(result.getName()).isEqualTo("한식");
        assertThat(result.getParentId()).isNull();
    }

    @Test
    @DisplayName("소분류 카테고리 생성 성공")
    void createCategory_sub_success() throws Exception {
        UUID parentId = UUID.randomUUID();
        StoreCategory root = StoreCategory.create("한식", 1, null);
        setId(root, parentId);

        CreateStoreCategoryCommand command = CreateStoreCategoryCommand.builder()
                .name("국밥").sortOrder(1).parentId(parentId).build();

        when(storeCategoryRepository.findCategory(parentId)).thenReturn(Optional.of(root));
        when(storeCategoryRepository.existsActiveSiblingByName(eq("국밥"), eq(root), isNull())).thenReturn(false);
        when(storeCategoryRepository.existsActiveSiblingBySortOrder(eq(1), eq(root), isNull())).thenReturn(false);
        when(storeCategoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StoreCategoryResult result = storeCategoryService.createCategory(command);

        assertThat(result.getName()).isEqualTo("국밥");
    }

    @Test
    @DisplayName("같은 부모 아래 동명 카테고리 생성 시 예외")
    void createCategory_duplicate_name_throws() {
        CreateStoreCategoryCommand command = CreateStoreCategoryCommand.builder()
                .name("한식")
                .sortOrder(1)
                .parentId(null)
                .build();

        when(storeCategoryRepository.existsActiveSiblingByName(eq("한식"), isNull(), isNull()))
                .thenReturn(true);

        assertThatThrownBy(() -> storeCategoryService.createCategory(command))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.CATEGORY_DUPLICATE_NAME);
    }

    @Test
    @DisplayName("같은 부모 아래 동일 sortOrder 카테고리 생성 시 예외")
    void createCategory_duplicate_sort_order_throws() {
        CreateStoreCategoryCommand command = CreateStoreCategoryCommand.builder()
                .name("중식")
                .sortOrder(1)
                .parentId(null)
                .build();

        when(storeCategoryRepository.existsActiveSiblingByName(eq("중식"), isNull(), isNull()))
                .thenReturn(false);
        when(storeCategoryRepository.existsActiveSiblingBySortOrder(eq(1), isNull(), isNull()))
                .thenReturn(true);

        assertThatThrownBy(() -> storeCategoryService.createCategory(command))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.CATEGORY_DUPLICATE_SORT_ORDER);
    }

    @Test
    @DisplayName("2뎁스(소분류) 하위에 카테고리 생성 불가")
    void createCategory_under_sub_category_throws() {
        StoreCategory root = StoreCategory.create("한식", 1, null);
        StoreCategory sub = StoreCategory.create("국밥", 1, root);
        UUID subId = UUID.randomUUID();

        CreateStoreCategoryCommand command = CreateStoreCategoryCommand.builder()
                .name("뚝배기")
                .sortOrder(1)
                .parentId(subId)
                .build();

        when(storeCategoryRepository.findCategory(subId)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> storeCategoryService.createCategory(command))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.INVALID_CATEGORY);
    }

    // updateCategory

    @Test
    @DisplayName("카테고리 이름 수정 성공")
    void updateCategory_name_success() throws Exception {
        UUID categoryId = UUID.randomUUID();
        StoreCategory category = StoreCategory.create("한식", 1, null);
        setId(category, categoryId);

        UpdateStoreCategoryCommand command = UpdateStoreCategoryCommand.builder()
                .categoryId(categoryId).name("중식").sortOrder(null).build();

        when(storeCategoryRepository.findCategory(categoryId)).thenReturn(Optional.of(category));
        when(storeCategoryRepository.existsActiveSiblingByName(eq("중식"), isNull(), eq(categoryId))).thenReturn(false);

        StoreCategoryResult result = storeCategoryService.updateCategory(command);

        assertThat(result.getName()).isEqualTo("중식");
            // 카테고리명이 매장 목록 캐시(StoreResult.category)에 스냅샷돼 있으므로 변경 시 무효화되어야 함
        verify(storeListCacheRepository).evictAllAfterCommit();
    }

    @Test
    @DisplayName("수정 시 자신을 제외하고 이름 중복 검사 (excludeId = 자신의 categoryId)")
    void updateCategory_excludes_self_from_duplicate_check() throws Exception {
        UUID categoryId = UUID.randomUUID();
        StoreCategory category = StoreCategory.create("한식", 1, null);
        // @GeneratedValue는 JPA 영속 시점에만 동작 → 리플렉션으로 수동 주입
        Field field = StoreCategory.class.getDeclaredField("categoryId");
        field.setAccessible(true);
        field.set(category, categoryId);

        UpdateStoreCategoryCommand command = UpdateStoreCategoryCommand.builder()
                .categoryId(categoryId)
                .name("한식") // 이름 그대로 - 자신 제외 시 중복 없음
                .sortOrder(null)
                .build();

        when(storeCategoryRepository.findCategory(categoryId)).thenReturn(Optional.of(category));
        // excludeId = categoryId로 자신 제외 → 중복 없음
        when(storeCategoryRepository.existsActiveSiblingByName(eq("한식"), isNull(), eq(categoryId)))
                .thenReturn(false);

        storeCategoryService.updateCategory(command);
    }

    // deleteCategory

    @Test
    @DisplayName("소분류 카테고리 삭제 성공")
    void deleteCategory_sub_success() throws Exception {
        StoreCategory root = StoreCategory.create("한식", 1, null);
        StoreCategory sub = StoreCategory.create("국밥", 1, root);
        UUID subId = UUID.randomUUID();
        setId(sub, subId);
        UUID deletedBy = UUID.randomUUID();

        when(storeCategoryRepository.findCategory(subId)).thenReturn(Optional.of(sub));
        when(storeRepository.existsActiveStoreByCategory(sub)).thenReturn(false);

        assertThatCode(() -> storeCategoryService.deleteCategory(subId, deletedBy))
                .doesNotThrowAnyException();
        assertThat(sub.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("자식 카테고리가 있는 대분류 삭제 시 예외")
    void deleteCategory_with_active_children_throws() {
        StoreCategory root = StoreCategory.create("한식", 1, null);
        UUID rootId = UUID.randomUUID();

        when(storeCategoryRepository.findCategory(rootId)).thenReturn(Optional.of(root));
        when(storeCategoryRepository.existsActiveChildren(root)).thenReturn(true);

        assertThatThrownBy(() -> storeCategoryService.deleteCategory(rootId, UUID.randomUUID()))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.CATEGORY_HAS_CHILDREN);
    }

    @Test
    @DisplayName("활성 매장이 있는 소분류 삭제 시 예외")
    void deleteCategory_sub_with_active_stores_throws() {
        StoreCategory root = StoreCategory.create("한식", 1, null);
        StoreCategory sub = StoreCategory.create("국밥", 1, root);
        UUID subId = UUID.randomUUID();

        when(storeCategoryRepository.findCategory(subId)).thenReturn(Optional.of(sub));
        when(storeRepository.existsActiveStoreByCategory(sub)).thenReturn(true);

        assertThatThrownBy(() -> storeCategoryService.deleteCategory(subId, UUID.randomUUID()))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.CATEGORY_HAS_STORES);
    }

    // getAllCategories

    @Test
    @DisplayName("전체 카테고리 목록 반환")
    void getAllCategories_returns_list() {
        StoreCategory root = StoreCategory.create("한식", 1, null);
        StoreCategory sub = StoreCategory.create("국밥", 1, root);

        when(storeCategoryRepository.findAllCategories()).thenReturn(List.of(root, sub));

        List<StoreCategoryResult> results = storeCategoryService.getAllCategories();

        assertThat(results).hasSize(2);
    }

    // helpers

    private void setId(StoreCategory category, UUID id) throws Exception {
        Field field = StoreCategory.class.getDeclaredField("categoryId");
        field.setAccessible(true);
        field.set(category, id);
    }
}
