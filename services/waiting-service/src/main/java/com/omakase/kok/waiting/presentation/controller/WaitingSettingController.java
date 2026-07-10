package com.omakase.kok.waiting.presentation.controller;

import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.auth.RoleAuthorizationUtils;
import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.waiting.application.service.WaitingSettingService;
import com.omakase.kok.waiting.presentation.dto.request.WaitingSettingUpdateRequest;
import com.omakase.kok.waiting.presentation.dto.response.WaitingSettingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/waitings/stores/{storeId}/settings")
@Tag(name = "Waiting Setting", description = "매장 웨이팅 설정 API")
@SecurityRequirement(name = "Bearer Authentication")
public class WaitingSettingController {
    private final WaitingSettingService waitingSettingService;

    @Operation(summary = "웨이팅 설정 수정", description = "OWNER 또는 MASTER가 매장의 웨이팅 설정을 수정")
    @PatchMapping
    public ResponseEntity<ApiResponse<WaitingSettingResponse>> updateWaitingSetting(
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(value = AuthConstants.ROLE, required = false) String role,
            @PathVariable UUID storeId,
            @Valid @RequestBody WaitingSettingUpdateRequest request
    ) {
        RoleAuthorizationUtils.requireAnyRole(
                role,
                AuthConstants.MASTER,
                AuthConstants.OWNER
        );
        WaitingSettingResponse response = waitingSettingService.updateWaitingSetting(userId, role, storeId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
