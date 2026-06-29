package com.omakase.kok.notification.integration;

import com.omakase.kok.notification.application.service.NotificationSaveService;
import com.omakase.kok.notification.domain.entity.SlackSendLog;
import com.omakase.kok.notification.domain.enums.NotificationType;
import com.omakase.kok.notification.domain.enums.ReferenceType;
import com.omakase.kok.notification.domain.repository.NotificationRepository;
import com.omakase.kok.notification.domain.repository.SlackSendLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("알림 저장 TX 롤백 통합 테스트")
class NotificationSaveServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired NotificationSaveService notificationSaveService;
    @Autowired NotificationRepository notificationRepository;

    // SlackSendLogRepository를 MockitoBean으로 교체 — save() 호출 시 예외 유도
    @MockitoBean SlackSendLogRepository slackSendLogRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    @Test
    @DisplayName("SlackSendLog 저장 실패 시 TX 롤백으로 Notification도 미저장")
    void slackSendLog_Save_Failed_Notification_No_Save() {
        when(slackSendLogRepository.save(any(SlackSendLog.class)))
                .thenThrow(new RuntimeException("강제 저장 실패"));

        assertThatThrownBy(() -> notificationSaveService.save(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReferenceType.WAITING,
                NotificationType.WAITING_REGISTERED,
                Map.of(
                        "storeName", "테스트 식당",
                        "waitingNumber", 1,
                        "peopleCount", 2
                )
        )).isInstanceOf(RuntimeException.class);

        // TX 롤백으로 Notification도 저장되지 않아야 함
        assertThat(notificationRepository.count()).isEqualTo(0);
    }
}
