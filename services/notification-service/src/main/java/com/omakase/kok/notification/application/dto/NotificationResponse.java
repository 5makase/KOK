package com.omakase.kok.notification.application.dto;

import com.omakase.kok.notification.domain.entity.Notification;
import com.omakase.kok.notification.domain.enums.NotificationType;
import com.omakase.kok.notification.domain.enums.ReferenceType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class NotificationResponse {

    private UUID notificationId;
    private UUID referenceId;
    private ReferenceType referenceType;
    private NotificationType notificationType;
    private String message;
    private boolean isRead;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification notification) {
        return NotificationResponse.builder()
                .notificationId(notification.getNotificationId())
                .referenceId(notification.getReferenceId())
                .referenceType(notification.getReferenceType())
                .notificationType(notification.getNotificationType())
                .message(notification.getMessage())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
