package com.omakase.kok.notification.application.service;

import com.omakase.kok.notification.domain.enums.NotificationType;
import com.omakase.kok.notification.domain.enums.ReferenceType;
import com.omakase.kok.notification.infrastructure.messaging.event.NotificationEvent;
import com.omakase.kok.notification.infrastructure.messaging.event.ProducerEventMapper;
import com.omakase.kok.notification.infrastructure.redis.RedisIdempotencyService;
import com.omakase.kok.notification.infrastructure.redis.RedisUnreadCountService;
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
    private final SlackSendService slackSendService;
    private final RedisIdempotencyService redisIdempotencyService;
    private final RedisUnreadCountService redisUnreadCountService;

    public void process(NotificationEvent event) {
        NotificationType notificationType = NotificationType.valueOf(event.getEventType());
        ReferenceType referenceType = ProducerEventMapper.toReferenceType(event.getProducer());
        UUID userId = ProducerEventMapper.toUserId(event.getPayload());
        UUID referenceId = ProducerEventMapper.toReferenceId(event.getProducer(), event.getPayload());

        // 1. eventId 기반 중복 체크
        if (event.getEventId() != null && !redisIdempotencyService.tryAcquireByEventId(event.getEventId())) {
            log.info("[NotificationEventService] 중복 이벤트 skip (eventId). eventId={}", event.getEventId());
            return;
        }

        // 2. 비즈니스 레벨 중복 체크
        if (!redisIdempotencyService.tryAcquire(referenceId, notificationType, userId)) {
            log.info("[NotificationEventService] 중복 이벤트 skip (비즈니스). referenceId={}, type={}",
                    referenceId, event.getEventType());
            return;
        }

        // 3. DB 저장
        NotificationSaveService.SaveResult result;
        try {
            result = notificationSaveService.save(userId, referenceId, referenceType, notificationType, event.getPayload());
        } catch (DataIntegrityViolationException e) {
            log.info("[NotificationEventService] 중복 이벤트 skip (DB). referenceId={}, type={}",
                    referenceId, event.getEventType());
            return;
        }

        // 4. 미읽음 카운트 증가 (TX 커밋 후)
        redisUnreadCountService.increment(userId);

        // 5. Slack 발송
        slackSendService.send(userId, result.notification().getMessage(), result.slackSendLog());
    }
}
