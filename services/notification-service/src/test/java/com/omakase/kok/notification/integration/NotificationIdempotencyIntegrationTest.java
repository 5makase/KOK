package com.omakase.kok.notification.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.notification.application.service.NotificationEventService;
import com.omakase.kok.notification.domain.repository.NotificationRepository;
import com.omakase.kok.notification.domain.repository.SlackSendLogRepository;
import com.omakase.kok.notification.infrastructure.messaging.event.NotificationEvent;
import com.omakase.kok.notification.infrastructure.redis.RedisIdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("멱등성 통합 테스트")
class NotificationIdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired NotificationEventService notificationEventService;
    @Autowired NotificationRepository notificationRepository;
    @Autowired SlackSendLogRepository slackSendLogRepository;
    @Autowired RedisIdempotencyService redisIdempotencyService;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        slackSendLogRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("동일 이벤트 2회 수신 시 알림 1건만 저장")
    void same_Event_One_Save() throws Exception {
        NotificationEvent event = buildWaitingEvent(UUID.randomUUID(), UUID.randomUUID());

        notificationEventService.process(event);
        notificationEventService.process(event); // 두 번째 — Redis NX 차단

        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(slackSendLogRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Redis TTL 만료 후 DB unique constraint가 3단계 방어")
    void redis_TTL_DB_unique_constraint() throws Exception {
        NotificationEvent event = buildWaitingEvent(UUID.randomUUID(), UUID.randomUUID());

        notificationEventService.process(event); // 1회 — 정상 저장

        // Redis key 강제 삭제 (TTL 만료 시뮬레이션)
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        notificationEventService.process(event); // 2회 — Redis 통과, DB constraint 위반 catch → skip

        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    // ── helper ───────────────────────────────────────────

    private NotificationEvent buildWaitingEvent(UUID waitingId, UUID userId) throws Exception {
        String json = """
                {
                    "eventId": "%s",
                    "eventType": "WAITING_REGISTERED",
                    "schemaVersion": 1,
                    "occurredAt": "2024-01-01T00:00:00",
                    "producer": "waiting-service",
                    "payload": {
                        "waitingId": "%s",
                        "userId": "%s",
                        "storeName": "테스트 식당",
                        "waitingNumber": 1,
                        "peopleCount": 2
                    }
                }
                """.formatted(UUID.randomUUID(), waitingId, userId);
        return objectMapper.readValue(json, NotificationEvent.class);
    }
}
