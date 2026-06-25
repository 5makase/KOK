package com.omakase.kok.notification.infrastructure.scheduler;

import com.omakase.kok.notification.application.service.SlackSendService;
import com.omakase.kok.notification.domain.entity.Notification;
import com.omakase.kok.notification.domain.entity.SlackSendLog;
import com.omakase.kok.notification.domain.enums.NotificationSendStatus;
import com.omakase.kok.notification.domain.repository.NotificationRepository;
import com.omakase.kok.notification.domain.repository.SlackSendLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RetryScheduler {

    private final SlackSendLogRepository slackSendLogRepository;
    private final NotificationRepository notificationRepository;
    private final SlackSendService slackSendService;

    @Value("${notification.retry.max-attempt-count:3}")
    private int maxAttemptCount;

    @Scheduled(fixedDelayString = "${notification.retry.fixed-delay-ms:300000}")
    @SchedulerLock(name = "retryFailedSlackSend", lockAtLeastFor = "PT1M", lockAtMostFor = "PT9M")
    public void retryFailedSlackSend() {
        List<SlackSendLog> targets = slackSendLogRepository.findRetryTargets(
                NotificationSendStatus.FAILED, maxAttemptCount
        );

        if (targets.isEmpty()) {
            return;
        }

        log.info("[RetryScheduler] 재시도 대상 {}건", targets.size());

        List<UUID> notificationIds = targets.stream()
                .map(SlackSendLog::getNotificationId)
                .toList();

        Map<UUID, Notification> notificationMap = notificationRepository
                .findAllByNotificationIdInAndDeletedAtIsNull(notificationIds)
                .stream()
                .collect(Collectors.toMap(Notification::getNotificationId, n -> n));

        for (SlackSendLog slackSendLog : targets) {
            Notification notification = notificationMap.get(slackSendLog.getNotificationId());

            if (notification == null) {
                log.warn("[RetryScheduler] 알림 없음. notificationId={}", slackSendLog.getNotificationId());
                continue;
            }

            try {
                slackSendService.send(notification.getUserId(), notification.getMessage(), slackSendLog);
            } catch (Exception e) {
                log.error("[RetryScheduler] 재시도 중 예외 발생. notificationId={}", slackSendLog.getNotificationId(), e);
            }
        }
    }
}
