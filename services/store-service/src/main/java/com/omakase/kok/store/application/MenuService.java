package com.omakase.kok.store.application;

import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.auth.RoleAuthorizationUtils;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.application.command.CreateMenuCommand;
import com.omakase.kok.store.application.command.UpdateMenuCommand;
import com.omakase.kok.store.application.result.MenuResult;
import com.omakase.kok.store.domain.entity.Menu;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.repository.MenuRepository;
import com.omakase.kok.store.domain.service.StoreFinder;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    private final MenuRepository menuRepository;
    private final StoreFinder storeFinder;

    @Transactional
    public MenuResult createMenu(CreateMenuCommand command, String role) {
        Store store = storeFinder.findActiveOrThrow(command.getStoreId());
        if (!RoleAuthorizationUtils.hasAnyRole(role, AuthConstants.MASTER)) {
            validateOwner(store, command.getRequesterId());
        }
        validateDisplayOrder(store, command.getDisplayOrder());
        Menu menu = Menu.create(store, command.getName(), command.getPrice(),
                command.getDescription(), command.getThumbnailUrl(), command.getDisplayOrder());
        return MenuResult.from(menuRepository.save(menu));
    }

    @Transactional
    public MenuResult updateMenu(UpdateMenuCommand command, String role) {
        Store store = storeFinder.findActiveOrThrow(command.getStoreId());
        if (!RoleAuthorizationUtils.hasAnyRole(role, AuthConstants.MASTER)) {
            validateOwner(store, command.getRequesterId());
        }
        Menu menu = findActiveMenuOrThrow(command.getMenuId(), store);
        // PATCH 부분 수정 - displayOrder가 전송된 경우에만 중복 체크
        if (command.getDisplayOrder() != null) {
            validateDisplayOrderForUpdate(store, command.getDisplayOrder(), menu.getMenuId());
        }
        menu.update(command.getName(), command.getPrice(), command.getDescription(),
                command.getThumbnailUrl(), command.getDisplayOrder());
        return MenuResult.from(menuRepository.save(menu));
    }

    @Transactional
    public MenuResult deleteMenu(UUID storeId, UUID menuId, UUID requesterId, String role) {
        Store store = storeFinder.findActiveOrThrow(storeId);
        if (!RoleAuthorizationUtils.hasAnyRole(role, AuthConstants.MASTER)) {
            validateOwner(store, requesterId);
        }
        // deletedAt IS NULL 조회 -> 이미 삭제된 메뉴는 MENU_NOT_FOUND(404)로 처리
        Menu menu = findActiveMenuOrThrow(menuId, store);
        menu.delete(requesterId);
        return MenuResult.from(menuRepository.save(menu));
    }

    public List<MenuResult> getMenus(UUID storeId) {
        Store store = storeFinder.findActiveOrThrow(storeId);
        return menuRepository.findAllMenus(store).stream()
                .map(MenuResult::from)
                .toList();
    }

    @Transactional
    public MenuResult toggleSoldOut(UUID storeId, UUID menuId, UUID requesterId, String role) {
        Store store = storeFinder.findActiveOrThrow(storeId);
        if (!RoleAuthorizationUtils.hasAnyRole(role, AuthConstants.MASTER)) {
            validateOwner(store, requesterId);
        }
        Menu menu = findActiveMenuOrThrow(menuId, store);
        // 현재 상태 판단
        if (menu.isSoldOut()) {
            menu.onSale();
        } else {
            menu.soldOut();
        }
        return MenuResult.from(menuRepository.save(menu));
    }

    private Menu findActiveMenuOrThrow(UUID menuId, Store store) {
        return menuRepository.findMenu(menuId, store)
                .orElseThrow(() -> new BaseException(StoreErrorCode.MENU_NOT_FOUND));
    }

    private void validateOwner(Store store, UUID requesterId) {
        if (!store.isOwnedBy(requesterId)) {
            throw new BaseException(StoreErrorCode.MENU_ACCESS_DENIED);
        }
    }

    private void validateDisplayOrder(Store store, int displayOrder) {
        if (menuRepository.isDuplicateDisplayOrder(store, displayOrder)) {
            throw new BaseException(StoreErrorCode.MENU_DUPLICATE_DISPLAY_ORDER);
        }
    }

    private void validateDisplayOrderForUpdate(Store store, int displayOrder, UUID menuId) {
        // 다른 필드만 수정할 때 기존 displayOrder를 그대로 보내도 중복으로 처리되지 않도록 자기 자신은 제외
        if (menuRepository.isDuplicateDisplayOrderExcluding(store, displayOrder, menuId)) {
            throw new BaseException(StoreErrorCode.MENU_DUPLICATE_DISPLAY_ORDER);
        }
    }
}
