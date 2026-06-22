package com.omakase.kok.notification.service;

import com.omakase.kok.notification.dto.response.NotificationResponse;
import com.omakase.kok.notification.dto.response.UnreadCountResponse;
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

    public Page<NotificationResponse> getNotifications(UUID userId, Pageable pageable) {
        return notificationRepository
                .findByUserIdAndDeletedAtIsNull(userId, pageable)
                .map(NotificationResponse::from);
    }

    public UnreadCountResponse getUnreadCount(UUID userId) {
        long count = notificationRepository.countByUserIdAndIsReadFalseAndDeletedAtIsNull(userId);
        return new UnreadCountResponse(count);
    }

    @Transactional
    public void markAsRead(UUID userId, UUID notificationId) {
        Notification notification = findOwnedNotification(userId, notificationId);
        notification.markAsRead();
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsRead(userId);
    }

    @Transactional
    public void deleteNotification(UUID userId, UUID notificationId) {
        Notification notification = findOwnedNotification(userId, notificationId);
        notification.delete(userId);
    }

    /**
     * 알림을 조회하되, userId 불일치 시에도 동일하게 404를 반환한다.
     * 타인의 알림 존재 여부를 노출하지 않기 위함.
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
