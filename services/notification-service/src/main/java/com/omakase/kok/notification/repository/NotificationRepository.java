package com.omakase.kok.notification.repository;

import com.omakase.kok.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdAndDeletedAtIsNull(UUID userId, Pageable pageable);

    long countByUserIdAndIsReadFalseAndDeletedAtIsNull(UUID userId);

    Optional<Notification> findByNotificationIdAndDeletedAtIsNull(UUID notificationId);

    List<Notification> findAllByNotificationIdInAndDeletedAtIsNull(List<UUID> notificationIds);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false AND n.deletedAt IS NULL")
    int markAllAsRead(@Param("userId") UUID userId);
}
