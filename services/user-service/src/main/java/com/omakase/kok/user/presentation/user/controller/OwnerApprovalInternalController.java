package com.omakase.kok.user.presentation.user.controller;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.user.application.user.service.OwnerApprovalService;
import com.omakase.kok.user.presentation.user.dto.response.OwnerApprovalStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 서비스 간 내부 호출 전용 (API Gateway InternalBlockFilter가 /api/v1/internal/** 외부 접근을 차단함)
@Tag(name = "Internal", description = "내부 서비스 통신 API")
@RestController
@RequestMapping("/api/v1/internal/users")
@RequiredArgsConstructor
public class OwnerApprovalInternalController {

    private final OwnerApprovalService ownerApprovalService;

    @Operation(summary = "OWNER 승인 상태 조회 (내부용)")
    @GetMapping("/{userId}/owner-approval")
    public ResponseEntity<ApiResponse<OwnerApprovalStatusResponse>> getOwnerApprovalStatus(
            @PathVariable UUID userId
    ) {
        OwnerApprovalStatusResponse response = ownerApprovalService.getApprovalStatus(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}