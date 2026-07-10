package com.omakase.kok.notification.presentation.controller;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.notification.application.dto.NotificationResponse;
import com.omakase.kok.notification.application.dto.UnreadCountResponse;
import com.omakase.kok.notification.application.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getNotifications(
            @RequestHeader("X-User-Id") UUID userId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getNotifications(userId, pageable)));
    }

    @Operation(summary = "미읽음 개수 조회")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getUnreadCount(userId)));
    }

    @Operation(summary = "알림 읽음 처리")
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID notificationId
    ) {
        notificationService.markAsRead(userId, notificationId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "전체 알림 읽음 처리")
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @RequestHeader("X-User-Id") UUID userId
    ) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "알림 삭제")
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID notificationId
    ) {
        notificationService.deleteNotification(userId, notificationId);
        return ResponseEntity.ok(ApiResponse.deleted());
    }
}
