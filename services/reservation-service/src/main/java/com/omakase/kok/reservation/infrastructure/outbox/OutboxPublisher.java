package com.omakase.kok.reservation.infrastructure.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.reservation.domain.entity.ReservationOutboxEvent;
import com.omakase.kok.reservation.domain.enums.OutboxEventStatus;
import com.omakase.kok.reservation.domain.repository.ReservationOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final String TOPIC = "reservation.events.v1";

    private final ReservationOutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedRate = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<ReservationOutboxEvent> events = outboxEventRepository.findByStatus(OutboxEventStatus.PENDING);

        for (ReservationOutboxEvent event : events) {
            try {
                String storeId = extractStoreId(event.getPayload());
                kafkaTemplate.send(TOPIC, storeId, event.getPayload()).get();
                event.published();
            } catch (Exception e) {
                log.error("Outbox 이벤트 발행 실패 - outboxEventId: {}, eventType: {}",
                        event.getOutboxEventId(), event.getEventType(), e);
                event.failed(e.getMessage());
            }
        }
    }

    private String extractStoreId(String payload) throws Exception {
        JsonNode node = objectMapper.readTree(payload);
        return node.get("storeId").asText();
    }
}
