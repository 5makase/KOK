package com.omakase.kok.notification.application.service;

import com.omakase.kok.notification.domain.entity.SlackSendLog;
import com.omakase.kok.notification.domain.repository.SlackSendLogRepository;
import com.omakase.kok.notification.infrastructure.client.SlackClient;
import com.omakase.kok.notification.infrastructure.client.UserServiceClient;
import com.omakase.kok.notification.infrastructure.client.dto.UserResponse;
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
            log.info("[SlackSendService] Slack 이메일 미등록. userId={}", userId);
            slackSendLog.markAsSkipped();
            slackSendLogRepository.save(slackSendLog);
            return null;
        }

        try {
            return slackClient.lookupByEmail(user.getSlackId());
        } catch (Exception e) {
            // users_not_found: Slack 워크스페이스에 해당 이메일의 계정이 없는 경우
            log.info("[SlackSendService] Slack 이메일 조회 실패. userId={}, slackEmail={}, error={}",
                    userId, user.getSlackId(), e.getMessage());
            slackSendLog.markAsSkipped();
            slackSendLogRepository.save(slackSendLog);
            return null;
        }
    }
}
