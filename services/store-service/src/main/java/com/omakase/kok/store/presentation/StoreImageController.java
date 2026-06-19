package com.omakase.kok.store.presentation;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.store.application.StoreImageService;
import com.omakase.kok.store.application.command.AddStoreImageCommand;
import com.omakase.kok.store.application.command.UpdateStoreImageCommand;
import com.omakase.kok.store.application.result.StoreImageResult;
import com.omakase.kok.store.presentation.dto.request.AddStoreImageRequest;
import com.omakase.kok.store.presentation.dto.request.UpdateStoreImageRequest;
import com.omakase.kok.store.presentation.dto.response.StoreImageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/stores/{storeId}/images")
@RequiredArgsConstructor
public class StoreImageController {

    private final StoreImageService storeImageService;

    // 이미지 bulk 등록 (1장~N장, 동일 슬롯 이미 존재 시 URL 업데이트)
    @PostMapping
    public ResponseEntity<ApiResponse<List<StoreImageResponse>>> addImages(
            @PathVariable UUID storeId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody AddStoreImageRequest request
    ) {
        List<StoreImageResult> results = storeImageService.addImages(
                AddStoreImageCommand.of(storeId, userId, request));
        List<StoreImageResponse> response = results.stream().map(StoreImageResponse::from).toList();
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    // 이미지 단건 수정 (URL 또는 displayOrder 변경, displayOrder 충돌 시 기존 이미지 soft delete)
    @PatchMapping("/{imageId}")
    public ResponseEntity<ApiResponse<StoreImageResponse>> updateImage(
            @PathVariable UUID storeId,
            @PathVariable UUID imageId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UpdateStoreImageRequest request
    ) {
        StoreImageResult result = storeImageService.updateImage(
                UpdateStoreImageCommand.of(storeId, imageId, userId, request));
        return ResponseEntity.ok(ApiResponse.success(StoreImageResponse.from(result)));
    }

    // 이미지 삭제 (Soft Delete)
    @DeleteMapping("/{imageId}")
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @PathVariable UUID storeId,
            @PathVariable UUID imageId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        storeImageService.deleteImage(storeId, imageId, userId);
        return ResponseEntity.ok(ApiResponse.deleted());
    }

    // 매장 이미지 목록 조회 (displayOrder 오름차순)
    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreImageResponse>>> getImages(
            @PathVariable UUID storeId
    ) {
        List<StoreImageResponse> response = storeImageService.getImages(storeId).stream()
                .map(StoreImageResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
