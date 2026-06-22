package com.omakase.kok.notification.service;

import com.omakase.kok.notification.entity.Notification;
import com.omakase.kok.notification.entity.SlackSendLog;
import com.omakase.kok.notification.enums.NotificationType;
import com.omakase.kok.notification.enums.ReferenceType;
import com.omakase.kok.notification.repository.NotificationRepository;
import com.omakase.kok.notification.repository.SlackSendLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * 알림 저장 트랜잭션 담당.
 *
 * NotificationEventService에서 직접 @Transactional 메서드를 호출하면
 * Spring AOP self-invocation으로 트랜잭션이 적용되지 않으므로 별도 빈으로 분리한다.
 */
@Service
@RequiredArgsConstructor
public class NotificationSaveService {

    private final NotificationRepository notificationRepository;
    private final SlackSendLogRepository slackSendLogRepository;

    public record SaveResult(Notification notification, SlackSendLog slackSendLog) {}

    @Transactional
    public SaveResult save(
            UUID userId,
            UUID referenceId,
            ReferenceType referenceType,
            NotificationType notificationType,
            Map<String, Object> params
    ) {
        Notification notification = notificationRepository.save(
                Notification.create(userId, referenceId, referenceType, notificationType, params)
        );

        SlackSendLog slackSendLog = slackSendLogRepository.save(
                SlackSendLog.create(notification.getNotificationId())
        );

        return new SaveResult(notification, slackSendLog);
    }
}
