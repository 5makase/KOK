package com.omakase.kok.reservation.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.reservation.domain.entity.ReservationOutboxEvent;
import com.omakase.kok.reservation.domain.enums.OutboxEventStatus;
import com.omakase.kok.reservation.domain.repository.ReservationOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final String TOPIC = "reservation.events.v1";
    private static final String LOCK_KEY = "outbox:publisher:lock";
    private static final int BATCH_SIZE = 100;
    private static final int KAFKA_TIMEOUT_SECONDS = 5;
    private static final int FAILED_REASON_MAX_LENGTH = 500;

    private final ReservationOutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(fixedRate = 5000)
    public void publishPendingEvents() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            if (!lock.tryLock(0, 5, TimeUnit.SECONDS)) {
                return;
            }

            List<ReservationOutboxEvent> events = outboxEventRepository
                    .findByStatus(OutboxEventStatus.PENDING, PageRequest.of(0, BATCH_SIZE));

            log.info("Outbox 발행 대상: {}건", events.size());

            for (ReservationOutboxEvent event : events) {
                processSingleEvent(event.getOutboxEventId());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Outbox Publisher 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void processSingleEvent(UUID outboxEventId) {
        // 이벤트별 독립 트랜잭션 — 1건 실패해도 나머지 커밋에 영향 없음
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.execute(status -> {
            ReservationOutboxEvent event = outboxEventRepository.findById(outboxEventId).orElse(null);
            if (event == null || event.getStatus() != OutboxEventStatus.PENDING) {
                return null;
            }
            try {
                String storeId = extractStoreId(event.getPayload());
                kafkaTemplate.send(TOPIC, storeId, event.getPayload()).get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                event.published();
            } catch (JsonProcessingException | IllegalArgumentException e) {
                // 영구 실패 - payload 형식 오류, 재시도 불필요
                log.error("Outbox payload 오류 (재시도 불가) - outboxEventId: {}", outboxEventId, e);
                for (int i = 0; i < 5; i++) {
                    event.failed(truncate(e.getMessage()));
                }
            } catch (Exception e) {
                // 일시 실패 - Kafka 네트워크 오류 등, 재시도 가능
                log.error("Outbox 이벤트 발행 실패 - outboxEventId: {}, eventType: {}",
                        outboxEventId, event.getEventType(), e);
                event.failed(truncate(e.getMessage()));
            }
            return null;
        });
    }

    private String extractStoreId(String payload) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(payload);
        JsonNode storeIdNode = node.path("payload").path("storeId");
        if (!storeIdNode.isTextual() || storeIdNode.asText().isBlank()) {
            throw new IllegalArgumentException("payload에 storeId 필드가 없습니다: " + payload);
        }
        return storeIdNode.asText();
    }

    private String truncate(String message) {
        if (message == null) return null;
        return message.length() > FAILED_REASON_MAX_LENGTH
                ? message.substring(0, FAILED_REASON_MAX_LENGTH)
                : message;
    }
}
