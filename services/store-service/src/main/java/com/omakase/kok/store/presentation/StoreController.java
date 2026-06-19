package com.omakase.kok.store.presentation;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.common.dto.PageResponse;
import com.omakase.kok.store.application.StoreService;
import com.omakase.kok.store.application.command.ChangeStoreStatusCommand;
import com.omakase.kok.store.application.command.CreateStoreCommand;
import com.omakase.kok.store.application.command.UpdateStoreCommand;
import com.omakase.kok.store.application.result.StoreResult;
import com.omakase.kok.store.domain.enums.AmenityType;
import com.omakase.kok.store.domain.enums.StoreStatus;
import com.omakase.kok.store.domain.repository.StoreSearchCondition;
import com.omakase.kok.store.presentation.dto.request.ChangeStoreStatusRequest;
import com.omakase.kok.store.presentation.dto.request.CreateStoreRequest;
import com.omakase.kok.store.presentation.dto.request.UpdateStoreRequest;
import com.omakase.kok.store.presentation.dto.response.StoreResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    // 매장 등록
    @PostMapping
    public ResponseEntity<ApiResponse<StoreResponse>> createStore(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateStoreRequest request
    ) {
        StoreResult result = storeService.createStore(CreateStoreCommand.of(userId, request));
        return ResponseEntity.status(201).body(ApiResponse.created(StoreResponse.from(result)));
    }

    // 매장 기본정보 수정
    @PatchMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreResponse>> updateStore(
            @PathVariable UUID storeId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UpdateStoreRequest request
    ) {
        StoreResult result = storeService.updateStore(UpdateStoreCommand.of(storeId, userId, request));
        return ResponseEntity.ok(ApiResponse.success(StoreResponse.from(result)));
    }

    // 매장 상태 변경
    @PatchMapping("/{storeId}/status")
    public ResponseEntity<ApiResponse<StoreResponse>> changeStatus(
            @PathVariable UUID storeId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody ChangeStoreStatusRequest request
    ) {
        StoreResult result = storeService.changeStatus(ChangeStoreStatusCommand.of(storeId, userId, request));
        return ResponseEntity.ok(ApiResponse.success(StoreResponse.from(result)));
    }

    // 매장 상세 조회
    @GetMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreResponse>> getStore(
            @PathVariable UUID storeId
    ) {
        return ResponseEntity.ok(ApiResponse.success(StoreResponse.from(storeService.getStore(storeId))));
    }

    // 매장 목록 검색 (카테고리, 지역, 편의시설, 키워드, 정렬 조건 조합, ownerId 지정 시 내 매장 목록으로 동작)
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<StoreResponse>>> searchStores(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String sido,
            @RequestParam(required = false) String sigungu,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<AmenityType> amenities,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) StoreStatus status,
            @RequestParam(required = false) StoreSearchCondition.SortType sort,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        StoreSearchCondition condition = StoreSearchCondition.builder()
                .categoryId(categoryId)
                .sido(sido)
                .sigungu(sigungu)
                .keyword(keyword)
                .amenities(amenities)
                .ownerId(ownerId)
                .status(status)
                .sort(sort)
                .build();

        Page<StoreResult> page = storeService.searchStores(condition, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(page.map(StoreResponse::from))));
    }
}
