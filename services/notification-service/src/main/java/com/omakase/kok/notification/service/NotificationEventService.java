package com.omakase.kok.notification.service;

import com.omakase.kok.notification.enums.NotificationType;
import com.omakase.kok.notification.enums.ReferenceType;
import com.omakase.kok.notification.event.NotificationEvent;
import com.omakase.kok.notification.event.ProducerEventMapper;
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

        // 1. Redis 멱등성 사전 체크
        if (!redisIdempotencyService.tryAcquire(referenceId, notificationType, userId)) {
            log.info("[NotificationEventService] 중복 이벤트 skip (Redis). referenceId={}, type={}",
                    referenceId, event.getEventType());
            return;
        }

        // 2. DB 저장 (UNIQUE 제약이 최종 보루)
        NotificationSaveService.SaveResult result;
        try {
            result = notificationSaveService.save(userId, referenceId, referenceType, notificationType, event.getPayload());
        } catch (DataIntegrityViolationException e) {
            log.info("[NotificationEventService] 중복 이벤트 skip (DB). referenceId={}, type={}",
                    referenceId, event.getEventType());
            return;
        }

        // 3. 미읽음 카운트 증가 (TX 커밋 후)
        redisUnreadCountService.increment(userId);

        // 4. Slack 발송
        slackSendService.send(userId, result.notification().getMessage(), result.slackSendLog());
    }
}
