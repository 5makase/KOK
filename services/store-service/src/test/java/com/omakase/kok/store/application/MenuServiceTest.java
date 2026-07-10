package com.omakase.kok.store.application;

import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.store.application.validator.StoreOwnerValidator;
import org.mockito.Spy;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.application.command.CreateMenuCommand;
import com.omakase.kok.store.application.command.UpdateMenuCommand;
import com.omakase.kok.store.application.result.MenuResult;
import com.omakase.kok.store.domain.entity.Menu;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreCategory;
import com.omakase.kok.store.domain.repository.MenuRepository;
import com.omakase.kok.store.domain.service.StoreFinder;
import com.omakase.kok.store.domain.vo.Address;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock MenuRepository menuRepository;
    @Mock StoreFinder storeFinder;
    @Spy StoreOwnerValidator storeOwnerValidator = new StoreOwnerValidator();

    @InjectMocks
    MenuService menuService;

    private UUID ownerId;
    private UUID storeId;
    private UUID menuId;
    private Store store;
    private Menu menu;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        storeId = UUID.randomUUID();
        menuId = UUID.randomUUID();
        store = Store.create(ownerId, StoreCategory.create("한식", 1, null), "테스트 매장", null,
                new Address("서울", "강남", null, null, null, null), null, null);
        menu = Menu.create(store, "된장찌개", 8000, "설명", null, 1);
    }

    // createMenu

    @Test
    @DisplayName("메뉴 등록 성공")
    void createMenu_success() {
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(menuRepository.isDuplicateDisplayOrder(store, 1)).thenReturn(false);
        when(menuRepository.save(any())).thenReturn(menu);

        MenuResult result = menuService.createMenu(createCommand(ownerId, 1), "OWNER");

        assertThat(result.getName()).isEqualTo("된장찌개");
        assertThat(result.isSoldOut()).isFalse();
    }

    @Test
    @DisplayName("MASTER는 소유자 검증 없이 메뉴 등록 가능")
    void createMenu_master_skips_owner_check() {
        UUID masterId = UUID.randomUUID();
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(menuRepository.isDuplicateDisplayOrder(store, 1)).thenReturn(false);
        when(menuRepository.save(any())).thenReturn(menu);

        MenuResult result = menuService.createMenu(createCommand(masterId, 1), AuthConstants.MASTER);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("OWNER가 타인 매장에 메뉴 등록 시 403")
    void createMenu_owner_cannot_register_to_others_store() {
        UUID otherId = UUID.randomUUID();
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);

        assertThatThrownBy(() -> menuService.createMenu(createCommand(otherId, 1), "OWNER"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.MENU_ACCESS_DENIED);
    }

    @Test
    @DisplayName("displayOrder 중복 시 409")
    void createMenu_duplicate_display_order_throws() {
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(menuRepository.isDuplicateDisplayOrder(store, 1)).thenReturn(true);

        assertThatThrownBy(() -> menuService.createMenu(createCommand(ownerId, 1), "OWNER"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.MENU_DUPLICATE_DISPLAY_ORDER);
    }

    // updateMenu

    @Test
    @DisplayName("메뉴 수정 성공 - null 필드는 기존 값 유지")
    void updateMenu_success_partial() {
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(menuRepository.findMenu(menuId, store)).thenReturn(Optional.of(menu));
        when(menuRepository.save(any())).thenReturn(menu);

        UpdateMenuCommand command = UpdateMenuCommand.builder()
                .storeId(storeId).menuId(menuId).requesterId(ownerId)
                .name("김치찌개").price(null).description(null)
                .thumbnailUrl(null).displayOrder(null)
                .build();

        MenuResult result = menuService.updateMenu(command, "OWNER");

        assertThat(result.getName()).isEqualTo("김치찌개");
    }

    @Test
    @DisplayName("빈 문자열 name 전송 시 400")
    void updateMenu_blank_name_throws() {
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(menuRepository.findMenu(menuId, store)).thenReturn(Optional.of(menu));

        UpdateMenuCommand command = UpdateMenuCommand.builder()
                .storeId(storeId).menuId(menuId).requesterId(ownerId)
                .name("   ").price(null).description(null)
                .thumbnailUrl(null).displayOrder(null)
                .build();

        assertThatThrownBy(() -> menuService.updateMenu(command, "OWNER"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.MENU_INVALID_NAME);
    }

    @Test
    @DisplayName("수정 시 displayOrder 자기 자신 제외 중복 체크")
    void updateMenu_display_order_excludes_self() {
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(menuRepository.findMenu(menuId, store)).thenReturn(Optional.of(menu));
        // 자기 자신을 제외하면 중복 없음
        when(menuRepository.isDuplicateDisplayOrderExcluding(store, 1, menu.getMenuId())).thenReturn(false);
        when(menuRepository.save(any())).thenReturn(menu);

        UpdateMenuCommand command = UpdateMenuCommand.builder()
                .storeId(storeId).menuId(menuId).requesterId(ownerId)
                .name(null).price(null).description(null)
                .thumbnailUrl(null).displayOrder(1) // 기존과 동일 순서
                .build();

        MenuResult result = menuService.updateMenu(command, "OWNER");

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("수정 시 다른 메뉴와 displayOrder 중복이면 409")
    void updateMenu_display_order_conflict_throws() {
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(menuRepository.findMenu(menuId, store)).thenReturn(Optional.of(menu));
        when(menuRepository.isDuplicateDisplayOrderExcluding(store, 2, menu.getMenuId())).thenReturn(true);

        UpdateMenuCommand command = UpdateMenuCommand.builder()
                .storeId(storeId).menuId(menuId).requesterId(ownerId)
                .name(null).price(null).description(null)
                .thumbnailUrl(null).displayOrder(2)
                .build();

        assertThatThrownBy(() -> menuService.updateMenu(command, "OWNER"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.MENU_DUPLICATE_DISPLAY_ORDER);
    }

    @Test
    @DisplayName("OWNER가 타인 매장 메뉴 수정 시 403")
    void updateMenu_owner_cannot_update_others() {
        UUID otherId = UUID.randomUUID();
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);

        UpdateMenuCommand command = UpdateMenuCommand.builder()
                .storeId(storeId).menuId(menuId).requesterId(otherId)
                .name("변경").price(null).description(null)
                .thumbnailUrl(null).displayOrder(null)
                .build();

        assertThatThrownBy(() -> menuService.updateMenu(command, "OWNER"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.MENU_ACCESS_DENIED);
    }

    // deleteMenu

    @Test
    @DisplayName("메뉴 삭제 성공")
    void deleteMenu_success() {
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(menuRepository.findMenu(menuId, store)).thenReturn(Optional.of(menu));
        when(menuRepository.save(any())).thenReturn(menu);

        MenuResult result = menuService.deleteMenu(storeId, menuId, ownerId, "OWNER");

        assertThat(result.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 메뉴 삭제 시 404")
    void deleteMenu_not_found_throws() {
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(menuRepository.findMenu(menuId, store)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuService.deleteMenu(storeId, menuId, ownerId, "OWNER"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.MENU_NOT_FOUND);
    }

    @Test
    @DisplayName("MASTER는 소유자 검증 없이 메뉴 삭제 가능")
    void deleteMenu_master_skips_owner_check() {
        UUID masterId = UUID.randomUUID();
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(menuRepository.findMenu(menuId, store)).thenReturn(Optional.of(menu));
        when(menuRepository.save(any())).thenReturn(menu);

        MenuResult result = menuService.deleteMenu(storeId, menuId, masterId, AuthConstants.MASTER);

        assertThat(result).isNotNull();
    }

    // getMenus

    @Test
    @DisplayName("메뉴 목록 조회 - displayOrder 오름차순")
    void getMenus_returns_sorted_list() {
        Menu menu2 = Menu.create(store, "김치찌개", 9000, null, null, 2);
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(menuRepository.findAllMenus(store)).thenReturn(List.of(menu, menu2));

        List<MenuResult> results = menuService.getMenus(storeId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getDisplayOrder()).isEqualTo(1);
        assertThat(results.get(1).getDisplayOrder()).isEqualTo(2);
    }

    // toggleSoldOut

    @Test
    @DisplayName("판매 중 메뉴 → 품절 처리")
    void toggleSoldOut_on_sale_to_sold_out() {
        assertThat(menu.isSoldOut()).isFalse();
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(menuRepository.findMenu(menuId, store)).thenReturn(Optional.of(menu));
        when(menuRepository.save(any())).thenReturn(menu);

        MenuResult result = menuService.toggleSoldOut(storeId, menuId, ownerId, "OWNER");

        assertThat(result.isSoldOut()).isTrue();
    }

    @Test
    @DisplayName("품절 메뉴 → 판매 재개")
    void toggleSoldOut_sold_out_to_on_sale() {
        menu.soldOut();
        assertThat(menu.isSoldOut()).isTrue();
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(menuRepository.findMenu(menuId, store)).thenReturn(Optional.of(menu));
        when(menuRepository.save(any())).thenReturn(menu);

        MenuResult result = menuService.toggleSoldOut(storeId, menuId, ownerId, "OWNER");

        assertThat(result.isSoldOut()).isFalse();
    }

    @Test
    @DisplayName("OWNER가 타인 매장 품절 토글 시 403")
    void toggleSoldOut_owner_cannot_toggle_others() {
        UUID otherId = UUID.randomUUID();
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);

        assertThatThrownBy(() -> menuService.toggleSoldOut(storeId, menuId, otherId, "OWNER"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.MENU_ACCESS_DENIED);
    }

    // helpers

    private CreateMenuCommand createCommand(UUID requesterId, int displayOrder) {
        return CreateMenuCommand.builder()
                .storeId(storeId)
                .requesterId(requesterId)
                .name("된장찌개")
                .price(8000)
                .description("설명")
                .thumbnailUrl(null)
                .displayOrder(displayOrder)
                .build();
    }
}
