package com.omakase.kok.notification.entity;

import com.omakase.kok.notification.enums.NotificationSendStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_slack_send_logs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SlackSendLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "log_id", columnDefinition = "uuid")
    private UUID logId;

    @Column(name = "notification_id", nullable = false, columnDefinition = "uuid")
    private UUID notificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private NotificationSendStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "failed_reason", length = 200)
    private String failedReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static SlackSendLog create(UUID notificationId) {
        return SlackSendLog.builder()
                .notificationId(notificationId)
                .status(NotificationSendStatus.PENDING)
                .attemptCount(0)
                .build();
    }

    public void markAsSent() {
        this.status = NotificationSendStatus.SENT;
    }

    public void markAsFailed(String reason) {
        this.status = NotificationSendStatus.FAILED;
        this.attemptCount++;
        this.failedReason = reason;
    }

    public void markAsSkipped() {
        this.status = NotificationSendStatus.SKIPPED;
    }

    public boolean canRetry() {
        return this.status == NotificationSendStatus.FAILED && this.attemptCount <= 3;
    }
}
