package com.omakase.kok.store.presentation;

import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.auth.RoleAuthorizationUtils;
import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.store.application.StoreCategoryService;
import com.omakase.kok.store.application.command.CreateStoreCategoryCommand;
import com.omakase.kok.store.application.command.UpdateStoreCategoryCommand;
import com.omakase.kok.store.presentation.dto.request.CreateStoreCategoryRequest;
import com.omakase.kok.store.presentation.dto.request.UpdateStoreCategoryRequest;
import com.omakase.kok.store.presentation.dto.response.StoreCategoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// MASTER 전용 카테고리 관리 API
@Tag(name = "Admin-StoreCategory", description = "매장 카테고리 관리자 API")
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
public class AdminStoreCategoryController {

    private final StoreCategoryService storeCategoryService;

    // 카테고리 등록
    @Operation(summary = "카테고리 등록")
    @PostMapping
    public ResponseEntity<ApiResponse<StoreCategoryResponse.Single>> createCategory(
            @RequestHeader(AuthConstants.ROLE) String role,
            @Valid @RequestBody CreateStoreCategoryRequest request
    ) {
        RoleAuthorizationUtils.requireRole(role, AuthConstants.MASTER);
        CreateStoreCategoryCommand command = CreateStoreCategoryCommand.builder()
                .name(request.getName())
                .sortOrder(request.getSortOrder())
                .parentId(request.getParentId())
                .build();
        return ResponseEntity.status(201).body(ApiResponse.created(StoreCategoryResponse.Single.from(storeCategoryService.createCategory(command))));
    }

    // 카테고리 수정
    @Operation(summary = "카테고리 수정")
    @PatchMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<StoreCategoryResponse.Single>> updateCategory(
            @PathVariable UUID categoryId,
            @RequestHeader(AuthConstants.ROLE) String role,
            @Valid @RequestBody UpdateStoreCategoryRequest request
    ) {
        RoleAuthorizationUtils.requireRole(role, AuthConstants.MASTER);
        UpdateStoreCategoryCommand command = UpdateStoreCategoryCommand.builder()
                .categoryId(categoryId)
                .name(request.getName())
                .sortOrder(request.getSortOrder())
                .build();
        return ResponseEntity.ok(ApiResponse.success(StoreCategoryResponse.Single.from(storeCategoryService.updateCategory(command))));
    }

    // 카테고리 삭제
    @Operation(summary = "카테고리 삭제")
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @PathVariable UUID categoryId,
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(AuthConstants.ROLE) String role
    ) {
        RoleAuthorizationUtils.requireRole(role, AuthConstants.MASTER);
        storeCategoryService.deleteCategory(categoryId, userId);
        return ResponseEntity.ok(ApiResponse.deleted());
    }
}
