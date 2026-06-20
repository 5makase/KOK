package com.omakase.kok.store.presentation;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.store.application.StoreCategoryService;
import com.omakase.kok.store.presentation.dto.response.StoreCategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 카테고리 목록 조회 - 전체 공개
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class StoreCategoryController {

    private final StoreCategoryService storeCategoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreCategoryResponse>>> getAllCategories() {
        List<StoreCategoryResponse> response = storeCategoryService.getAllCategories().stream()
                .map(StoreCategoryResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
