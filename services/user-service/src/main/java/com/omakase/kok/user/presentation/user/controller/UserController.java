package com.omakase.kok.user.presentation.user.controller;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.user.application.user.service.OwnerApprovalService;
import com.omakase.kok.user.global.exception.UserErrorCode;
import com.omakase.kok.user.application.user.service.UserQueryService;
import com.omakase.kok.user.application.user.service.UserService;
import com.omakase.kok.user.presentation.user.dto.request.ApprovalRequest;
import com.omakase.kok.user.presentation.user.dto.request.SignupRequest;
import com.omakase.kok.user.presentation.user.dto.response.OwnerApprovalResponse;
import com.omakase.kok.user.presentation.user.dto.response.SignupResponse;
import com.omakase.kok.user.presentation.user.dto.response.UserDetailResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserQueryService userQueryService;
    private final OwnerApprovalService ownerApprovalService;

    // ──────────────────────────── 회원가입 ────────────────────────────

    @PostMapping("/users/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signupUser(
            @Valid @RequestBody SignupRequest request) {
        SignupResponse response = userService.signupUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @PostMapping("/owners/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signupOwner(
            @Valid @RequestBody SignupRequest request) {
        SignupResponse response = userService.signupOwner(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    // ──────────────────────────── 회원 조회 ────────────────────────────

    @GetMapping("/users/me")
    public ResponseEntity<ApiResponse<UserDetailResponse>> getMyInfo(
            @RequestHeader("X-User-Id") UUID userId) {
        UserDetailResponse response = userQueryService.getMyInfo(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<UserDetailResponse>> getUserById(
            @PathVariable UUID userId,
            @RequestHeader("X-Role") String role) {
        if (!"MASTER".equals(role)) {
            throw new BaseException(UserErrorCode.USER_ACCESS_DENIED);
        }
        UserDetailResponse response = userQueryService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ──────────────────── OWNER 승인/거절 (MASTER 전용) ────────────────────

    @GetMapping("/admin/owners/approvals")
    public ResponseEntity<ApiResponse<List<OwnerApprovalResponse>>> getPendingApprovals(
            @RequestHeader("X-Role") String role) {
        if (!"MASTER".equals(role)) {
            throw new BaseException(UserErrorCode.USER_ACCESS_DENIED); // 수정: OWNER_APPROVAL_NOT_FOUND → USER_ACCESS_DENIED
        }
        List<OwnerApprovalResponse> response = userQueryService.getPendingApprovals();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/admin/owners/approvals/{approvalId}/approve")
    public ResponseEntity<ApiResponse<OwnerApprovalResponse>> approveOwner(
            @PathVariable UUID approvalId,
            @RequestHeader("X-User-Id") UUID masterUserId,
            @RequestHeader("X-Role") String role) {
        if (!"MASTER".equals(role)) {
            throw new BaseException(UserErrorCode.USER_ACCESS_DENIED); // 수정: OWNER_APPROVAL_NOT_FOUND → USER_ACCESS_DENIED
        }
        OwnerApprovalResponse response = ownerApprovalService.approveOwner(approvalId, masterUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/admin/owners/approvals/{approvalId}/reject")
    public ResponseEntity<ApiResponse<OwnerApprovalResponse>> rejectOwner(
            @PathVariable UUID approvalId,
            @RequestHeader("X-User-Id") UUID masterUserId,
            @RequestHeader("X-Role") String role,
            @Valid @RequestBody ApprovalRequest request) {
        if (!"MASTER".equals(role)) {
            throw new BaseException(UserErrorCode.USER_ACCESS_DENIED); // 수정: OWNER_APPROVAL_NOT_FOUND → USER_ACCESS_DENIED
        }
        OwnerApprovalResponse response = ownerApprovalService.rejectOwner(approvalId, masterUserId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}