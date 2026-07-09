package com.omakase.kok.store.presentation;

import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.auth.RoleAuthorizationUtils;
import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.store.application.StoreAmenityService;
import com.omakase.kok.store.application.command.AddStoreAmenityCommand;
import com.omakase.kok.store.presentation.dto.request.AddStoreAmenityRequest;
import com.omakase.kok.store.presentation.dto.response.StoreAmenityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "StoreAmenity", description = "매장 편의시설 API")
@RestController
@RequestMapping("/api/v1/stores/{storeId}/amenities")
@RequiredArgsConstructor
public class StoreAmenityController {

    private final StoreAmenityService storeAmenityService;

    // 편의시설 동기화 - 요청 목록으로 전체 교체 (restore/insert/soft delete 자동 처리)
    @Operation(summary = "편의시설 동기화")
    @PutMapping
    public ResponseEntity<ApiResponse<StoreAmenityResponse.Bulk>> syncAmenities(
            @PathVariable UUID storeId,
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(AuthConstants.ROLE) String role,
            @Valid @RequestBody AddStoreAmenityRequest request
    ) {
        RoleAuthorizationUtils.requireAnyRole(role, AuthConstants.OWNER, AuthConstants.MASTER);
        AddStoreAmenityCommand command = AddStoreAmenityCommand.builder()
                .storeId(storeId)
                .requesterId(userId)
                .amenityTypes(request.getAmenityTypes())
                .build();
        return ResponseEntity.ok(ApiResponse.success(StoreAmenityResponse.Bulk.from(storeAmenityService.syncAmenities(command, role))));
    }

    // 편의시설 삭제 (Soft Delete)
    @Operation(summary = "편의시설 삭제")
    @DeleteMapping("/{amenityId}")
    public ResponseEntity<ApiResponse<Void>> deleteAmenity(
            @PathVariable UUID storeId,
            @PathVariable UUID amenityId,
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(AuthConstants.ROLE) String role
    ) {
        RoleAuthorizationUtils.requireAnyRole(role, AuthConstants.OWNER, AuthConstants.MASTER);
        storeAmenityService.deleteAmenity(storeId, amenityId, userId, role);
        return ResponseEntity.ok(ApiResponse.deleted());
    }

    // 매장 편의시설 목록 조회
    @Operation(summary = "매장 편의시설 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreAmenityResponse>>> getAmenities(
            @PathVariable UUID storeId
    ) {
        List<StoreAmenityResponse> response = storeAmenityService.getAmenities(storeId).stream()
                .map(StoreAmenityResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
