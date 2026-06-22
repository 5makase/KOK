package com.omakase.kok.notification.service;

import com.omakase.kok.notification.dto.NotificationResponse;
import com.omakase.kok.notification.dto.UnreadCountResponse;
import com.omakase.kok.notification.entity.Notification;
import com.omakase.kok.notification.exception.NotificationErrorCode;
import com.omakase.kok.notification.exception.NotificationException;
import com.omakase.kok.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final RedisUnreadCountService redisUnreadCountService;

    public Page<NotificationResponse> getNotifications(UUID userId, Pageable pageable) {
        return notificationRepository
                .findByUserIdAndDeletedAtIsNull(userId, pageable)
                .map(NotificationResponse::from);
    }

    public UnreadCountResponse getUnreadCount(UUID userId) {
        return redisUnreadCountService.get(userId)
                .stream()
                .mapToObj(UnreadCountResponse::new)
                .findFirst()
                .orElseGet(() -> {
                    // 캐시 미스: DB 조회 후 Redis 재구성
                    long count = notificationRepository.countByUserIdAndIsReadFalseAndDeletedAtIsNull(userId);
                    redisUnreadCountService.set(userId, count);
                    return new UnreadCountResponse(count);
                });
    }

    @Transactional
    public void markAsRead(UUID userId, UUID notificationId) {
        Notification notification = findOwnedNotification(userId, notificationId);
        if (!notification.isRead()) {
            notification.markAsRead();
            redisUnreadCountService.decrement(userId);
        }
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        int updatedCount = notificationRepository.markAllAsRead(userId);
        if (updatedCount > 0) {
            redisUnreadCountService.set(userId, 0);
        }
    }

    @Transactional
    public void deleteNotification(UUID userId, UUID notificationId) {
        Notification notification = findOwnedNotification(userId, notificationId);
        if (!notification.isRead()) {
            redisUnreadCountService.decrement(userId);
        }
        notification.delete(userId);
    }

    /**
     * userId 불일치도 404로 처리 — 타인의 알림 존재 여부를 노출하지 않는다.
     */
    private Notification findOwnedNotification(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository
                .findByNotificationIdAndDeletedAtIsNull(notificationId)
                .orElseThrow(() -> new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getUserId().equals(userId)) {
            throw new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        }

        return notification;
    }
}
