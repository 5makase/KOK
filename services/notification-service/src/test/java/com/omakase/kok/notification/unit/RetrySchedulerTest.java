package com.omakase.kok.notification.unit;

import com.omakase.kok.notification.application.service.SlackSendService;
import com.omakase.kok.notification.domain.entity.Notification;
import com.omakase.kok.notification.domain.entity.SlackSendLog;
import com.omakase.kok.notification.domain.enums.NotificationSendStatus;
import com.omakase.kok.notification.domain.enums.NotificationType;
import com.omakase.kok.notification.domain.enums.ReferenceType;
import com.omakase.kok.notification.domain.repository.NotificationRepository;
import com.omakase.kok.notification.domain.repository.SlackSendLogRepository;
import com.omakase.kok.notification.infrastructure.scheduler.RetryScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("RetryScheduler 단위 테스트")
@ExtendWith(MockitoExtension.class)
class RetrySchedulerTest {

    @Mock SlackSendLogRepository slackSendLogRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock SlackSendService slackSendService;
    @InjectMocks RetryScheduler retryScheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(retryScheduler, "maxAttemptCount", 3);
    }

    @Test
    @DisplayName("FAILED 건 재시도 시 SlackSendService.send() 호출 확인")
    void FAILED_Ok() {
        UUID notificationId = UUID.randomUUID();
        SlackSendLog failedLog = SlackSendLog.create(notificationId);
        failedLog.markAsFailed("이전 실패");

        Notification notification = Notification.create(
                UUID.randomUUID(), UUID.randomUUID(),
                ReferenceType.WAITING, NotificationType.WAITING_REGISTERED,
                Map.of("storeName", "테스트", "waitingNumber", 1, "peopleCount", 2)
        );
        // @GeneratedValue라 DB 저장 없이는 notificationId = null
        // SlackSendLog의 notificationId와 일치시켜야 map 조회 성공
        ReflectionTestUtils.setField(notification, "notificationId", notificationId);

        when(slackSendLogRepository.findRetryTargets(NotificationSendStatus.FAILED, 3))
                .thenReturn(List.of(failedLog));
        when(notificationRepository.findAllByNotificationIdInAndDeletedAtIsNull(any()))
                .thenReturn(List.of(notification));

        retryScheduler.retryFailedSlackSend();

        verify(slackSendService).send(any(UUID.class), anyString(), eq(failedLog));
    }

    @Test
    @DisplayName("attempt_count 초과 건은 findRetryTargets 쿼리에서 제외 확인")
    void attempt_count() {
        when(slackSendLogRepository.findRetryTargets(NotificationSendStatus.FAILED, 3))
                .thenReturn(List.of());

        retryScheduler.retryFailedSlackSend();

        verify(slackSendService, never()).send(any(), anyString(), any());
    }

    @Test
    @DisplayName("SlackSendLog에 매칭되는 Notification 없을 때 send() 미호출")
    void Notification_send() {
        UUID notificationId = UUID.randomUUID();
        SlackSendLog failedLog = SlackSendLog.create(notificationId);
        failedLog.markAsFailed("이전 실패");

        when(slackSendLogRepository.findRetryTargets(NotificationSendStatus.FAILED, 3))
                .thenReturn(List.of(failedLog));
        when(notificationRepository.findAllByNotificationIdInAndDeletedAtIsNull(any()))
                .thenReturn(List.of());

        retryScheduler.retryFailedSlackSend();

        verify(slackSendService, never()).send(any(), anyString(), any());
    }
}
