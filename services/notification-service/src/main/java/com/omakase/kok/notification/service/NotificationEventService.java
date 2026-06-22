package com.omakase.kok.notification.service;

import com.omakase.kok.notification.client.SlackClient;
import com.omakase.kok.notification.client.UserServiceClient;
import com.omakase.kok.notification.client.dto.UserResponse;
import com.omakase.kok.notification.entity.SlackSendLog;
import com.omakase.kok.notification.enums.NotificationType;
import com.omakase.kok.notification.enums.ReferenceType;
import com.omakase.kok.notification.event.NotificationEvent;
import com.omakase.kok.notification.repository.SlackSendLogRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventService {

    private final NotificationSaveService notificationSaveService;
    private final SlackSendLogRepository slackSendLogRepository;
    private final UserServiceClient userServiceClient;
    private final SlackClient slackClient;

    public void process(NotificationEvent event) {
        NotificationType notificationType = NotificationType.valueOf(event.getEventType());
        ReferenceType referenceType = ReferenceType.valueOf(event.getReferenceType());

        NotificationSaveService.SaveResult result;
        try {
            result = notificationSaveService.save(event, notificationType, referenceType);
        } catch (DataIntegrityViolationException e) {
            log.info("[NotificationEventService] 중복 이벤트 skip. referenceId={}, type={}",
                    event.getReferenceId(), event.getEventType());
            return;
        }

        sendSlack(event.getUserId(), result.notification().getMessage(), result.slackSendLog());
    }

    private void sendSlack(UUID userId, String message, SlackSendLog slackSendLog) {
        String slackId = resolveSlackId(userId, slackSendLog);
        if (slackId == null) {
            return;
        }

        try {
            slackClient.sendDirectMessage(slackId, message);
            slackSendLog.markAsSent();
        } catch (Exception e) {
            slackSendLog.markAsFailed(e.getMessage());
            log.error("[NotificationEventService] Slack 발송 실패. notificationId={}",
                    slackSendLog.getNotificationId(), e);
        } finally {
            slackSendLogRepository.save(slackSendLog);
        }
    }

    /**
     * User Service에서 Slack ID를 조회한다.
     * 조회 실패 또는 미등록 시 SKIPPED 처리 후 null 반환.
     */
    private String resolveSlackId(UUID userId, SlackSendLog slackSendLog) {
        UserResponse user;
        try {
            user = userServiceClient.getUser(userId).getData();
        } catch (FeignException e) {
            log.warn("[NotificationEventService] User Service 호출 실패. userId={}", userId, e);
            slackSendLog.markAsSkipped();
            slackSendLogRepository.save(slackSendLog);
            return null;
        }

        if (user == null || user.getSlackId() == null || user.getSlackId().isBlank()) {
            log.info("[NotificationEventService] Slack ID 미등록. userId={}", userId);
            slackSendLog.markAsSkipped();
            slackSendLogRepository.save(slackSendLog);
            return null;
        }

        return user.getSlackId();
    }
}
