package com.omakase.kok.notification.unit;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.notification.domain.entity.SlackSendLog;
import com.omakase.kok.notification.domain.enums.NotificationSendStatus;
import com.omakase.kok.notification.domain.repository.SlackSendLogRepository;
import com.omakase.kok.notification.application.service.SlackSendService;
import com.omakase.kok.notification.infrastructure.client.SlackClient;
import com.omakase.kok.notification.infrastructure.client.UserServiceClient;
import com.omakase.kok.notification.infrastructure.client.dto.UserResponse;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("SlackSendService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class SlackSendServiceTest {

    @Mock SlackSendLogRepository slackSendLogRepository;
    @Mock UserServiceClient userServiceClient;
    @Mock SlackClient slackClient;
    @InjectMocks SlackSendService slackSendService;

    private UUID userId;
    private SlackSendLog slackSendLog;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        slackSendLog = SlackSendLog.create(UUID.randomUUID());
    }

    @Test
    @DisplayName("Slack API 실패 시 FAILED 기록 및 attemptCount 증가")
    void Slack_API_FAILED_Log() {
        UserResponse user = new UserResponse();
        user.setSlackId("test@example.com");
        when(userServiceClient.getUser(any(), anyString())).thenReturn(ApiResponse.success(user));
        when(slackClient.lookupByEmail("test@example.com")).thenReturn("U123456");
        doThrow(new RuntimeException("Slack API 오류")).when(slackClient).sendDirectMessage(anyString(), anyString());

        slackSendService.send(userId, "테스트 메시지", slackSendLog);

        assertThat(slackSendLog.getStatus()).isEqualTo(NotificationSendStatus.FAILED);
        assertThat(slackSendLog.getAttemptCount()).isEqualTo(1);
        verify(slackSendLogRepository).save(slackSendLog);
    }

    @Test
    @DisplayName("Slack API 성공 시 SENT 기록")
    void Slack_API_SENT_Log() {
        UserResponse user = new UserResponse();
        user.setSlackId("test@example.com");
        when(userServiceClient.getUser(any(), anyString())).thenReturn(ApiResponse.success(user));
        when(slackClient.lookupByEmail("test@example.com")).thenReturn("U123456");
        doNothing().when(slackClient).sendDirectMessage(anyString(), anyString());

        slackSendService.send(userId, "테스트 메시지", slackSendLog);

        assertThat(slackSendLog.getStatus()).isEqualTo(NotificationSendStatus.SENT);
        verify(slackSendLogRepository).save(slackSendLog);
    }

    @Test
    @DisplayName("User Service 장애 시 SKIPPED 처리 — RetryScheduler 재시도 대상 제외")
    void UserService_SKIPPED() {
        when(userServiceClient.getUser(any(), anyString()))
                .thenThrow(mock(FeignException.class));

        slackSendService.send(userId, "테스트 메시지", slackSendLog);

        assertThat(slackSendLog.getStatus()).isEqualTo(NotificationSendStatus.SKIPPED);
        verify(slackClient, never()).sendDirectMessage(anyString(), anyString());
        verify(slackSendLogRepository).save(slackSendLog);
    }

    @Test
    @DisplayName("slackId 미등록 유저 SKIPPED 처리")
    void no_slackId_SKIPPED() {
        UserResponse userWithNoSlack = new UserResponse(); // slackId = null
        when(userServiceClient.getUser(any(), anyString())).thenReturn(ApiResponse.success(userWithNoSlack));

        slackSendService.send(userId, "테스트 메시지", slackSendLog);

        assertThat(slackSendLog.getStatus()).isEqualTo(NotificationSendStatus.SKIPPED);
        verify(slackClient, never()).lookupByEmail(anyString());
    }
}
