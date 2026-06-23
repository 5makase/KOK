package com.omakase.kok.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.notification.event.NotificationEvent;
import com.omakase.kok.notification.service.NotificationEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingEventConsumer {

    private final NotificationEventService notificationEventService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "waiting.events.v1", groupId = "notification-service-group")
    public void consume(String message, Acknowledgment ack) {
        NotificationEvent event;
        try {
            event = objectMapper.readValue(message, NotificationEvent.class);
        } catch (JsonProcessingException e) {
            // Irrecoverable Error: 역직렬화 오류 — 재시도해도 해결 불가
            // TODO: waiting.events.v1.dlq 토픽으로 격리
            log.error("[WaitingEventConsumer] 역직렬화 실패 (Irrecoverable). message={}", message, e);
            ack.acknowledge();
            return;
        }

        if (event == null || !isValidEvent(event)) {
            // Irrecoverable Error: 필수 필드 누락
            // TODO: waiting.events.v1.dlq 토픽으로 격리
            log.error("[WaitingEventConsumer] 필수 필드 누락 (Irrecoverable). eventType={}, producer={}",
                    event.getEventType(), event.getProducer());
            ack.acknowledge();
            return;
        }

        try {
            notificationEventService.process(event);
            ack.acknowledge();
        } catch (Exception e) {
            // Recoverable Error: DB 일시 장애, 외부 연동 타임아웃 등
            // 현재: ack 후 RetryScheduler가 SlackSendLog FAILED 건 재시도
            // TODO: 필요 시 Backoff 재시도 후 waiting.events.v1.dlq 이관으로 전환
            log.error("[WaitingEventConsumer] 이벤트 처리 실패 (Recoverable). eventType={}", event.getEventType(), e);
            ack.acknowledge();
        }
    }

    private boolean isValidEvent(NotificationEvent event) {
        return event.getEventType() != null
                && event.getProducer() != null
                && event.getPayload() != null
                && !event.getPayload().isEmpty();
    }
}
