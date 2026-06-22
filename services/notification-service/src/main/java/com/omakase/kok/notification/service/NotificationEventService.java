package com.omakase.kok.notification.service;

import com.omakase.kok.notification.entity.Notification;
import com.omakase.kok.notification.entity.SlackSendLog;
import com.omakase.kok.notification.enums.NotificationType;
import com.omakase.kok.notification.enums.ReferenceType;
import com.omakase.kok.notification.event.NotificationEvent;
import com.omakase.kok.notification.repository.NotificationRepository;
import com.omakase.kok.notification.repository.SlackSendLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventService {

    private final NotificationRepository notificationRepository;
    private final SlackSendLogRepository slackSendLogRepository;
    // TODO: RedisIdempotencyService redisIdempotencyService;
    // TODO: SlackClient slackClient;
    // TODO: UserServiceClient userServiceClient;

    public void process(NotificationEvent event) {
        NotificationType notificationType = NotificationType.valueOf(event.getEventType());
        ReferenceType referenceType = ReferenceType.valueOf(event.getReferenceType());

        // 1. DB 저장 (트랜잭션 내)
        SlackSendLog slackSendLog;
        try {
            slackSendLog = saveNotificationAndLog(event, notificationType, referenceType);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE 제약 위반 → 중복 이벤트 → skip
            log.info("[NotificationEventService] 중복 이벤트 skip. referenceId={}, type={}",
                    event.getReferenceId(), event.getEventType());
            return;
        }

        // 2. Slack 발송 (트랜잭션 밖)
        sendSlack(event, slackSendLog);
    }

    @Transactional
    protected SlackSendLog saveNotificationAndLog(
            NotificationEvent event,
            NotificationType notificationType,
            ReferenceType referenceType
    ) {
        Notification notification = notificationRepository.save(
                Notification.create(
                        event.getUserId(),
                        event.getReferenceId(),
                        referenceType,
                        notificationType,
                        event.getParams()
                )
        );

        return slackSendLogRepository.save(
                SlackSendLog.create(notification.getNotificationId())
        );
    }

    private void sendSlack(NotificationEvent event, SlackSendLog slackSendLog) {
        // TODO: User Service에서 slack_id 조회
        // String slackId = userServiceClient.getSlackId(event.getUserId());

        // TODO: slack_id 없으면 SKIPPED 처리
        // if (slackId == null) {
        //     slackSendLog.markAsSkipped();
        //     slackSendLogRepository.save(slackSendLog);
        //     return;
        // }

        // TODO: Slack 발송
        // try {
        //     slackClient.send(slackId, notification.getMessage());
        //     slackSendLog.markAsSent();
        // } catch (Exception e) {
        //     slackSendLog.markAsFailed(e.getMessage());
        //     log.error("[NotificationEventService] Slack 발송 실패. notificationId={}", slackSendLog.getNotificationId(), e);
        // } finally {
        //     slackSendLogRepository.save(slackSendLog);
        // }
    }
}
