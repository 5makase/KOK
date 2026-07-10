package com.omakase.kok.waiting.presentation.controller;

import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.auth.RoleAuthorizationUtils;
import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.common.dto.PageResponse;
import com.omakase.kok.waiting.application.service.WaitingService;
import com.omakase.kok.waiting.domain.enums.WaitingStatus;
import com.omakase.kok.waiting.presentation.dto.request.WaitingCancelRequest;
import com.omakase.kok.waiting.presentation.dto.request.WaitingCreateRequest;
import com.omakase.kok.waiting.presentation.dto.request.WaitingNoShowRequest;
import com.omakase.kok.waiting.presentation.dto.response.StoreWaitingResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingCallResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingCancelResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingDetailResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingEnterResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingNoShowResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/waitings")
@Tag(name = "Waiting", description = "웨이팅 API")
@SecurityRequirement(name = "Bearer Authentication")
public class WaitingController {
    private final WaitingService waitingService;

    @Operation(summary = "웨이팅 등록", description = "사용자가 매장에 웨이팅을 등록")
    @PostMapping
    public ResponseEntity<ApiResponse<WaitingResponse>> createWaiting(
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(value = AuthConstants.ROLE, required = false) String role,
            @Valid @RequestBody WaitingCreateRequest request
    ) {
        RoleAuthorizationUtils.requireAnyRole(
                role,
                AuthConstants.MASTER,
                AuthConstants.OWNER,
                AuthConstants.USER
        );
        WaitingResponse response = waitingService.createWaiting(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    @Operation(summary = "내 웨이팅 목록 조회", description = "사용자의 웨이팅 목록을 상태별로 페이징 조회")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<WaitingResponse>>> getMyWaitings(
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(value = AuthConstants.ROLE, required = false) String role,
            @RequestParam(required = false) WaitingStatus status,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        RoleAuthorizationUtils.requireAnyRole(
                role,
                AuthConstants.MASTER,
                AuthConstants.OWNER,
                AuthConstants.USER
        );
        PageResponse<WaitingResponse> response = waitingService.getMyWaitings(userId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "웨이팅 상세 조회", description = "웨이팅 ID로 웨이팅 상세 정보를 조회")
    @GetMapping("/{waitingId}")
    public ResponseEntity<ApiResponse<WaitingDetailResponse>> getWaiting(
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(value = AuthConstants.ROLE, required = false) String role,
            @PathVariable UUID waitingId
    ) {
        RoleAuthorizationUtils.requireAnyRole(
                role,
                AuthConstants.MASTER,
                AuthConstants.OWNER,
                AuthConstants.USER
        );
        WaitingDetailResponse response = waitingService.getWaiting(userId, role, waitingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "매장별 웨이팅 현황 조회", description = "OWNER 또는 MASTER가 매장의 웨이팅 현황을 상태별로 페이징 조회")
    @GetMapping("/stores/{storeId}")
    public ResponseEntity<ApiResponse<PageResponse<StoreWaitingResponse>>> getStoreWaitings(
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(value = AuthConstants.ROLE, required = false) String role,
            @PathVariable UUID storeId,
            @RequestParam(required = false) WaitingStatus status,
            @ParameterObject @PageableDefault(sort = "waitingNumber", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        RoleAuthorizationUtils.requireAnyRole(
                role,
                AuthConstants.MASTER,
                AuthConstants.OWNER
        );
        PageResponse<StoreWaitingResponse> response = waitingService.getStoreWaitings(userId, role, storeId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "웨이팅 취소", description = "사용자, OWNER 또는 MASTER가 웨이팅을 취소")
    @PatchMapping("/{waitingId}/cancel")
    public ResponseEntity<ApiResponse<WaitingCancelResponse>> cancelWaiting(
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(value = AuthConstants.ROLE, required = false) String role,
            @PathVariable UUID waitingId,
            @Valid @RequestBody WaitingCancelRequest request
    ) {
        RoleAuthorizationUtils.requireAnyRole(
                role,
                AuthConstants.MASTER,
                AuthConstants.OWNER,
                AuthConstants.USER
        );
        WaitingCancelResponse response = waitingService.cancelWaiting(userId, role, waitingId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "다음 순번 웨이팅 호출", description = "OWNER 또는 MASTER가 매장의 다음 웨이팅을 호출")
    @PostMapping("/stores/{storeId}/call-next")
    public ResponseEntity<ApiResponse<WaitingCallResponse>> callNextWaiting(
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(value = AuthConstants.ROLE, required = false) String role,
            @PathVariable UUID storeId
    ) {
        RoleAuthorizationUtils.requireAnyRole(
                role,
                AuthConstants.MASTER,
                AuthConstants.OWNER
        );
        WaitingCallResponse response = waitingService.callNextWaiting(userId, role, storeId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "웨이팅 입장 완료 처리", description = "OWNER 또는 MASTER가 호출된 웨이팅을 입장 완료 처리")
    @PatchMapping("/{waitingId}/enter")
    public ResponseEntity<ApiResponse<WaitingEnterResponse>> enterWaiting(
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(value = AuthConstants.ROLE, required = false) String role,
            @PathVariable UUID waitingId
    ) {
        RoleAuthorizationUtils.requireAnyRole(
                role,
                AuthConstants.MASTER,
                AuthConstants.OWNER
        );
        WaitingEnterResponse response = waitingService.enterWaiting(userId, role, waitingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "웨이팅 미입장 처리", description = "OWNER 또는 MASTER가 호출된 웨이팅을 미입장 처리")
    @PatchMapping("/{waitingId}/no-show")
    public ResponseEntity<ApiResponse<WaitingNoShowResponse>> noShowWaiting(
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(value = AuthConstants.ROLE, required = false) String role,
            @PathVariable UUID waitingId,
            @Valid @RequestBody WaitingNoShowRequest request
    ) {
        RoleAuthorizationUtils.requireAnyRole(
                role,
                AuthConstants.MASTER,
                AuthConstants.OWNER
        );
        WaitingNoShowResponse response = waitingService.noShowWaiting(userId, role, waitingId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
