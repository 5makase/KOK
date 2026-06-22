package com.omakase.kok.notification.service;

import com.omakase.kok.notification.client.SlackClient;
import com.omakase.kok.notification.client.UserServiceClient;
import com.omakase.kok.notification.dto.UserResponse;
import com.omakase.kok.notification.entity.SlackSendLog;
import com.omakase.kok.notification.repository.SlackSendLogRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackSendService {

    private final SlackSendLogRepository slackSendLogRepository;
    private final UserServiceClient userServiceClient;
    private final SlackClient slackClient;

    /**
     * Slack DM 발송 공통 로직.
     * 최초 발송(NotificationEventService)과 재시도(RetryScheduler) 모두에서 사용한다.
     */
    public void send(UUID userId, String message, SlackSendLog slackSendLog) {
        String slackId = resolveSlackId(userId, slackSendLog);
        if (slackId == null) {
            return;
        }

        try {
            slackClient.sendDirectMessage(slackId, message);
            slackSendLog.markAsSent();
        } catch (Exception e) {
            slackSendLog.markAsFailed(e.getMessage());
            log.error("[SlackSendService] Slack 발송 실패. notificationId={}",
                    slackSendLog.getNotificationId(), e);
        } finally {
            slackSendLogRepository.save(slackSendLog);
        }
    }

    private String resolveSlackId(UUID userId, SlackSendLog slackSendLog) {
        UserResponse user;
        try {
            user = userServiceClient.getUser(userId).getData();
        } catch (FeignException e) {
            log.warn("[SlackSendService] User Service 호출 실패. userId={}", userId, e);
            slackSendLog.markAsSkipped();
            slackSendLogRepository.save(slackSendLog);
            return null;
        }

        if (user == null || user.getSlackId() == null || user.getSlackId().isBlank()) {
            log.info("[SlackSendService] Slack ID 미등록. userId={}", userId);
            slackSendLog.markAsSkipped();
            slackSendLogRepository.save(slackSendLog);
            return null;
        }

        return user.getSlackId();
    }
}
