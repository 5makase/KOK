package com.omakase.kok.store.presentation;

import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.store.application.MenuService;
import com.omakase.kok.store.application.command.CreateMenuCommand;
import com.omakase.kok.store.application.command.UpdateMenuCommand;
import com.omakase.kok.store.presentation.dto.request.CreateMenuRequest;
import com.omakase.kok.store.presentation.dto.request.UpdateMenuRequest;
import com.omakase.kok.store.presentation.dto.response.MenuCreateResponse;
import com.omakase.kok.store.presentation.dto.response.MenuDeleteResponse;
import com.omakase.kok.store.presentation.dto.response.MenuDetailResponse;
import com.omakase.kok.store.presentation.dto.response.MenuSoldOutResponse;
import com.omakase.kok.store.presentation.dto.response.MenuUpdateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    // 메뉴 등록 (OWNER/MASTER)
    @PostMapping
    public ResponseEntity<ApiResponse<MenuCreateResponse>> createMenu(
            @PathVariable UUID storeId,
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(AuthConstants.ROLE) String role,
            @Valid @RequestBody CreateMenuRequest request
    ) {
        CreateMenuCommand command = CreateMenuCommand.of(storeId, userId, role, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(MenuCreateResponse.from(menuService.createMenu(command))));
    }

    // 메뉴 수정 (OWNER/MASTER)
    @PatchMapping("/{menuId}")
    public ResponseEntity<ApiResponse<MenuUpdateResponse>> updateMenu(
            @PathVariable UUID storeId,
            @PathVariable UUID menuId,
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(AuthConstants.ROLE) String role,
            @Valid @RequestBody UpdateMenuRequest request
    ) {
        UpdateMenuCommand command = UpdateMenuCommand.of(storeId, menuId, userId, role, request);
        return ResponseEntity.ok(ApiResponse.success(MenuUpdateResponse.from(menuService.updateMenu(command))));
    }

    // 메뉴 삭제 (OWNER/MASTER)
    @DeleteMapping("/{menuId}")
    public ResponseEntity<ApiResponse<MenuDeleteResponse>> deleteMenu(
            @PathVariable UUID storeId,
            @PathVariable UUID menuId,
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(AuthConstants.ROLE) String role
    ) {
        return ResponseEntity.ok(ApiResponse.success(MenuDeleteResponse.from(
                menuService.deleteMenu(storeId, menuId, userId, role))));
    }

    // 메뉴 목록 조회 (ALL)
    @GetMapping
    public ResponseEntity<ApiResponse<List<MenuDetailResponse>>> getMenus(
            @PathVariable UUID storeId
    ) {
        List<MenuDetailResponse> response = menuService.getMenus(storeId).stream()
                .map(MenuDetailResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 품절 토글 (OWNER/MASTER)
    @PatchMapping("/{menuId}/sold-out")
    public ResponseEntity<ApiResponse<MenuSoldOutResponse>> toggleSoldOut(
            @PathVariable UUID storeId,
            @PathVariable UUID menuId,
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(AuthConstants.ROLE) String role
    ) {
        return ResponseEntity.ok(ApiResponse.success(MenuSoldOutResponse.from(
                menuService.toggleSoldOut(storeId, menuId, userId, role))));
    }
}
