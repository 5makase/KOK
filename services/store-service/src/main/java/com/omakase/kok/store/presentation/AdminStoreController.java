package com.omakase.kok.store.presentation;

import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.auth.RoleAuthorizationUtils;
import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.store.application.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// MASTER 전용 매장 관리 API
@Tag(name = "Admin-Store", description = "매장 관리자 API")
@RestController
@RequestMapping("/api/v1/admin/stores")
@RequiredArgsConstructor
public class AdminStoreController {

    private final StoreService storeService;

    // 매장 삭제
    @Operation(summary = "매장 삭제")
    @DeleteMapping("/{storeId}")
    public ResponseEntity<ApiResponse<Void>> deleteStore(
            @PathVariable UUID storeId,
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(AuthConstants.ROLE) String role
    ) {
        RoleAuthorizationUtils.requireRole(role, AuthConstants.MASTER);
        storeService.deleteStore(storeId, userId, role);
        return ResponseEntity.ok(ApiResponse.deleted());
    }
}
