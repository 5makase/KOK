package com.omakase.kok.reservation.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omakase.kok.reservation.domain.entity.ReservationOutboxEvent;
import com.omakase.kok.reservation.domain.enums.EventType;
import com.omakase.kok.reservation.domain.enums.OutboxEventStatus;
import com.omakase.kok.reservation.domain.repository.ReservationOutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPublisher 단위 테스트")
class OutboxPublisherTest {

    @Mock private ReservationOutboxEventRepository outboxEventRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private RedissonClient redissonClient;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private RLock rLock;

    private OutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(
                outboxEventRepository,
                kafkaTemplate,
                objectMapper,
                redissonClient,
                transactionManager
        );
    }

    private String buildEnvelopePayload(UUID storeId) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reservationId", UUID.randomUUID().toString());
        payload.put("userId", UUID.randomUUID().toString());
        payload.put("storeId", storeId.toString());
        payload.put("visitedAt", null);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", "RESERVATION_CONFIRMED");
        envelope.put("schemaVersion", 1);
        envelope.put("occurredAt", "2026-06-23T10:00:00");
        envelope.put("producer", "reservation-service");
        envelope.put("payload", payload);

        return objectMapper.writeValueAsString(envelope);
    }

    private ReservationOutboxEvent buildPendingEvent(String payload) {
        return ReservationOutboxEvent.builder()
                .outboxEventId(UUID.randomUUID())
                .reservationId(UUID.randomUUID())
                .eventType(EventType.RESERVATION_CONFIRMED)
                .payload(payload)
                .build();
    }

    private void givenLockAcquired() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    private void givenTransactionExecutes() {
        TransactionStatus txStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(txStatus);
    }

    @Nested
    @DisplayName("publishPendingEvents()")
    class PublishPendingEvents {

        @Test
        @DisplayName("분산락 획득 실패 시 Outbox 조회를 하지 않는다")
        void skip_whenLockNotAcquired() throws Exception {
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);
            when(rLock.isHeldByCurrentThread()).thenReturn(false);

            outboxPublisher.publishPendingEvents();

            verify(outboxEventRepository, never()).findByStatus(any(), any());
        }

        @Test
        @DisplayName("PENDING 이벤트가 없으면 Kafka 발행을 하지 않는다")
        void skip_whenNoEvents() throws Exception {
            givenLockAcquired();
            when(outboxEventRepository.findByStatus(eq(OutboxEventStatus.PENDING), any(PageRequest.class)))
                    .thenReturn(List.of());

            outboxPublisher.publishPendingEvents();

            verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Kafka 발행 성공 시 이벤트 상태가 PUBLISHED로 변경된다")
        void success_eventPublished() throws Exception {
            UUID storeId = UUID.randomUUID();
            String payload = buildEnvelopePayload(storeId);
            ReservationOutboxEvent event = buildPendingEvent(payload);
            UUID eventId = event.getOutboxEventId();

            givenLockAcquired();
            when(outboxEventRepository.findByStatus(eq(OutboxEventStatus.PENDING), any(PageRequest.class)))
                    .thenReturn(List.of(event));
            givenTransactionExecutes();
            when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

            doReturn(CompletableFuture.completedFuture(null))
                    .when(kafkaTemplate).send(anyString(), anyString(), anyString());

            outboxPublisher.publishPendingEvents();

            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
            assertThat(event.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("올바른 Event Envelope 형식에서 storeId가 Kafka 파티션 키로 사용된다")
        void success_extractStoreIdFromEnvelope() throws Exception {
            UUID storeId = UUID.randomUUID();
            String payload = buildEnvelopePayload(storeId);
            ReservationOutboxEvent event = buildPendingEvent(payload);
            UUID eventId = event.getOutboxEventId();

            givenLockAcquired();
            when(outboxEventRepository.findByStatus(eq(OutboxEventStatus.PENDING), any(PageRequest.class)))
                    .thenReturn(List.of(event));
            givenTransactionExecutes();
            when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));
            doReturn(CompletableFuture.completedFuture(null))
                    .when(kafkaTemplate).send(anyString(), anyString(), anyString());

            outboxPublisher.publishPendingEvents();

            verify(kafkaTemplate).send("reservation.events.v1", storeId.toString(), payload);
        }

        @Test
        @DisplayName("payload에 storeId가 없는 잘못된 형식이면 이벤트가 FAILED로 처리된다")
        void fail_invalidPayloadFormat() throws Exception {
            // envelope 구조 없이 storeId가 루트에만 있는 구형 페이로드
            String oldFormatPayload = objectMapper.writeValueAsString(
                    Map.of("storeId", UUID.randomUUID().toString())
            );
            ReservationOutboxEvent event = buildPendingEvent(oldFormatPayload);
            UUID eventId = event.getOutboxEventId();

            givenLockAcquired();
            when(outboxEventRepository.findByStatus(eq(OutboxEventStatus.PENDING), any(PageRequest.class)))
                    .thenReturn(List.of(event));
            givenTransactionExecutes();
            when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

            outboxPublisher.publishPendingEvents();

            // payload.storeId가 없으므로 IllegalArgumentException → FAILED (5회 즉시 처리)
            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
            verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("이미 PUBLISHED 상태인 이벤트는 재발행하지 않는다")
        void skip_alreadyPublished() throws Exception {
            UUID storeId = UUID.randomUUID();
            String payload = buildEnvelopePayload(storeId);
            ReservationOutboxEvent event = buildPendingEvent(payload);
            event.published(); // PUBLISHED 상태로 전이
            UUID eventId = event.getOutboxEventId();

            givenLockAcquired();
            when(outboxEventRepository.findByStatus(eq(OutboxEventStatus.PENDING), any(PageRequest.class)))
                    .thenReturn(List.of(event));
            givenTransactionExecutes();
            when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

            outboxPublisher.publishPendingEvents();

            verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Kafka 발행 실패 시 이벤트 retryCount가 1 증가하고 PENDING 상태를 유지한다")
        void fail_kafkaError_incrementsRetryCount() throws Exception {
            UUID storeId = UUID.randomUUID();
            String payload = buildEnvelopePayload(storeId);
            ReservationOutboxEvent event = buildPendingEvent(payload);
            UUID eventId = event.getOutboxEventId();

            givenLockAcquired();
            when(outboxEventRepository.findByStatus(eq(OutboxEventStatus.PENDING), any(PageRequest.class)))
                    .thenReturn(List.of(event));
            givenTransactionExecutes();
            when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

            CompletableFuture<Object> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("Kafka timeout"));
            doReturn(failedFuture).when(kafkaTemplate).send(anyString(), anyString(), anyString());

            outboxPublisher.publishPendingEvents();

            assertThat(event.getRetryCount()).isEqualTo(1);
            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        }

        @Test
        @DisplayName("retryCount가 5에 도달하면 이벤트 상태가 FAILED로 변경된다")
        void fail_maxRetryReached_statusFailed() throws Exception {
            UUID storeId = UUID.randomUUID();
            String payload = buildEnvelopePayload(storeId);
            ReservationOutboxEvent event = buildPendingEvent(payload);
            // 4번 실패한 상태 시뮬레이션
            for (int i = 0; i < 4; i++) {
                event.failed("이전 실패");
            }
            UUID eventId = event.getOutboxEventId();

            givenLockAcquired();
            when(outboxEventRepository.findByStatus(eq(OutboxEventStatus.PENDING), any(PageRequest.class)))
                    .thenReturn(List.of(event));
            givenTransactionExecutes();
            when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

            CompletableFuture<Object> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("5번째 실패"));
            doReturn(failedFuture).when(kafkaTemplate).send(anyString(), anyString(), anyString());

            outboxPublisher.publishPendingEvents();

            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
            assertThat(event.getRetryCount()).isEqualTo(5);
        }
    }
}
