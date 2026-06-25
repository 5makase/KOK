package com.omakase.kok.notification.domain.entity;

import com.omakase.kok.common.entity.BaseEntity;
import com.omakase.kok.notification.domain.enums.NotificationType;
import com.omakase.kok.notification.domain.enums.ReferenceType;
import jakarta.persistence.*;
import lombok.*;


import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "p_notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "notification_id", columnDefinition = "uuid")
    private UUID notificationId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "reference_id", nullable = false, columnDefinition = "uuid")
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 30)
    private ReferenceType referenceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType notificationType;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    public static Notification create(
            UUID userId,
            UUID referenceId,
            ReferenceType referenceType,
            NotificationType notificationType,
            Map<String, Object> params
    ) {
        return Notification.builder()
                .userId(userId)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .notificationType(notificationType)
                .message(notificationType.render(params))
                .isRead(false)
                .build();
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
