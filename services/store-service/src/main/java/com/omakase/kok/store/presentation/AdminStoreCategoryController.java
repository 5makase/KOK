package com.omakase.kok.store.presentation;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.store.application.StoreCategoryService;
import com.omakase.kok.store.application.command.CreateStoreCategoryCommand;
import com.omakase.kok.store.application.command.UpdateStoreCategoryCommand;
import com.omakase.kok.store.application.result.StoreCategoryResult;
import com.omakase.kok.store.presentation.dto.request.CreateStoreCategoryRequest;
import com.omakase.kok.store.presentation.dto.request.UpdateStoreCategoryRequest;
import com.omakase.kok.store.presentation.dto.response.StoreCategoryResponse;
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
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
public class AdminStoreCategoryController {

    private final StoreCategoryService storeCategoryService;

    // 카테고리 등록
    @PostMapping
    public ResponseEntity<ApiResponse<StoreCategoryResponse>> createCategory(
            @Valid @RequestBody CreateStoreCategoryRequest request
    ) {
        StoreCategoryResult result = storeCategoryService.createCategory(
                CreateStoreCategoryCommand.from(request));
        return ResponseEntity.status(201).body(ApiResponse.created(StoreCategoryResponse.from(result)));
    }

    // 카테고리 수정
    @PatchMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<StoreCategoryResponse>> updateCategory(
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateStoreCategoryRequest request
    ) {
        StoreCategoryResult result = storeCategoryService.updateCategory(
                UpdateStoreCategoryCommand.of(categoryId, request));
        return ResponseEntity.ok(ApiResponse.success(StoreCategoryResponse.from(result)));
    }

    // 카테고리 삭제
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @PathVariable UUID categoryId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        storeCategoryService.deleteCategory(categoryId, userId);
        return ResponseEntity.ok(ApiResponse.deleted());
    }
}
