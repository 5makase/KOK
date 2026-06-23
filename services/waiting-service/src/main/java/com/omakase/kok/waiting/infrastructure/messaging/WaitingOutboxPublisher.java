package com.omakase.kok.waiting.infrastructure.messaging;

import com.omakase.kok.waiting.domain.entity.WaitingOutboxEvent;
import com.omakase.kok.waiting.domain.enums.OutboxStatus;
import com.omakase.kok.waiting.domain.repository.WaitingOutboxEventRepository;
import com.omakase.kok.waiting.infrastructure.config.WaitingKafkaProperties;
import com.omakase.kok.waiting.infrastructure.config.WaitingKafkaPublisherProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingOutboxPublisher {
    private final WaitingOutboxEventRepository waitingOutboxEventRepository;
    private final KafkaTemplate<String, String> waitingKafkaTemplate;
    private final WaitingKafkaProperties waitingKafkaProperties;
    private final WaitingKafkaPublisherProperties waitingKafkaPublisherProperties;

    // PENDING 상태의 Outbox 이벤트 발행
    @Scheduled(fixedDelayString = "${waiting.kafka.publisher.fixed-delay-ms}")
    @Transactional
    public void publishPendingEvents() {
        List<WaitingOutboxEvent> pendingEvents = loadEventsByStatus(OutboxStatus.PENDING);

        for (WaitingOutboxEvent pendingEvent : pendingEvents) {
            publishEvent(pendingEvent);
        }
    }

    // 실패 이벤트 재시도
    @Scheduled(fixedDelayString = "${waiting.kafka.publisher.failed-fixed-delay-ms}")
    @Transactional
    public void retryFailedEvents() {
        List<WaitingOutboxEvent> failedEvents = loadEventsByStatus(OutboxStatus.FAILED);

        for (WaitingOutboxEvent failedEvent : failedEvents) {
            failedEvent.retry();
            publishEvent(failedEvent);
        }
    }

    private List<WaitingOutboxEvent> loadEventsByStatus(OutboxStatus status) {
        return waitingOutboxEventRepository.findByStatusOrderByCreatedAtAsc(
                status,
                PageRequest.of(0, waitingKafkaPublisherProperties.batchSize())
        );
    }

    // 단건 Outbox 이벤트 발행
    private void publishEvent(WaitingOutboxEvent outboxEvent) {
        String topic = waitingKafkaProperties.name();
        String key = outboxEvent.getWaiting().getId().toString();

        try {
            waitingKafkaTemplate.send(topic, key, outboxEvent.getPayload())
                    .get(waitingKafkaPublisherProperties.sendTimeoutMs(), TimeUnit.MILLISECONDS);
            outboxEvent.publish();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outboxEvent.fail("Kafka publish interrupted");
            log.warn("Interrupted while publishing waiting outbox event. outboxEventId={}", outboxEvent.getId(), e);
        } catch (TimeoutException e) {
            outboxEvent.fail("Kafka publish timed out");
            log.warn("Timed out while publishing waiting outbox event. outboxEventId={}", outboxEvent.getId(), e);
        } catch (ExecutionException e) {
            outboxEvent.fail(resolveFailureReason(e));
            log.warn("Failed to publish waiting outbox event. outboxEventId={}", outboxEvent.getId(), e);
        }
    }

    // 발행 실패 사유 추출
    private String resolveFailureReason(Exception exception) {
        Throwable cause = exception.getCause();
        if (cause == null || cause.getMessage() == null || cause.getMessage().isBlank()) {
            return exception.getMessage();
        }
        return cause.getMessage();
    }
}
