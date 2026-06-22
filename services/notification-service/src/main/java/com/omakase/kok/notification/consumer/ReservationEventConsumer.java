package com.omakase.kok.notification.consumer;

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
public class ReservationEventConsumer {

    private final NotificationEventService notificationEventService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "reservation.events.v1", groupId = "notification-service-group")
    public void consume(String message, Acknowledgment ack) {
        try {
            NotificationEvent event = objectMapper.readValue(message, NotificationEvent.class);
            notificationEventService.process(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[ReservationEventConsumer] 이벤트 처리 실패. message={}", message, e);
            ack.acknowledge();
        }
    }
}
