package com.omakase.kok.notification.repository;

import com.omakase.kok.notification.entity.SlackSendLog;
import com.omakase.kok.notification.enums.NotificationSendStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlackSendLogRepository extends JpaRepository<SlackSendLog, UUID> {

    Optional<SlackSendLog> findByNotificationId(UUID notificationId);

    @Query("SELECT s FROM SlackSendLog s WHERE s.status = :status AND s.attemptCount <= :maxAttemptCount")
    List<SlackSendLog> findRetryTargets(
            @Param("status") NotificationSendStatus status,
            @Param("maxAttemptCount") int maxAttemptCount
    );
}
