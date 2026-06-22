package com.omakase.kok.notification.scheduler;

import com.omakase.kok.notification.entity.Notification;
import com.omakase.kok.notification.entity.SlackSendLog;
import com.omakase.kok.notification.enums.NotificationSendStatus;
import com.omakase.kok.notification.repository.NotificationRepository;
import com.omakase.kok.notification.repository.SlackSendLogRepository;
import com.omakase.kok.notification.service.SlackSendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Slack 발송 실패 건 재시도 스케줄러.
 *
 * NOTE: 멀티 인스턴스 환경에서는 중복 재시도가 발생할 수 있다.
 *       필요 시 ShedLock 또는 Redis 분산 락으로 보완해야 한다.
 */
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
    public void retryFailedSlackSend() {
        List<SlackSendLog> targets = slackSendLogRepository.findRetryTargets(
                NotificationSendStatus.FAILED, maxAttemptCount
        );

        if (targets.isEmpty()) {
            return;
        }

        log.info("[RetryScheduler] 재시도 대상 {}건", targets.size());

        for (SlackSendLog slackSendLog : targets) {
            Optional<Notification> notificationOpt = notificationRepository
                    .findByNotificationIdAndDeletedAtIsNull(slackSendLog.getNotificationId());

            if (notificationOpt.isEmpty()) {
                log.warn("[RetryScheduler] 알림 없음. notificationId={}", slackSendLog.getNotificationId());
                continue;
            }

            Notification notification = notificationOpt.get();
            slackSendService.send(notification.getUserId(), notification.getMessage(), slackSendLog);
        }
    }
}
