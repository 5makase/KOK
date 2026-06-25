package com.omakase.kok.waiting.infrastructure.messaging;

import com.omakase.kok.waiting.domain.entity.Waiting;
import com.omakase.kok.waiting.domain.entity.WaitingOutboxEvent;
import com.omakase.kok.waiting.domain.enums.OutboxStatus;
import com.omakase.kok.waiting.domain.enums.WaitingEventType;
import com.omakase.kok.waiting.domain.repository.WaitingOutboxEventRepository;
import com.omakase.kok.waiting.infrastructure.config.WaitingKafkaProperties;
import com.omakase.kok.waiting.infrastructure.config.WaitingKafkaPublisherProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class WaitingOutboxPublisherTest {
    @Mock
    private WaitingOutboxEventRepository waitingOutboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> waitingKafkaTemplate;

    @Test
    @DisplayName("실패한 아웃박스 이벤트를 다시 PENDING으로 돌린 뒤 재발행한다")
    void retryFailedEvents_republishesFailedOutboxEvents() {
        WaitingOutboxEvent failedEvent = WaitingOutboxEvent.builder()
                .waiting(waiting())
                .eventType(WaitingEventType.WAITING_REGISTERED)
                .payload("{\"waitingId\":\"test\"}")
                .build();
        failedEvent.fail("temporary failure");

        given(waitingOutboxEventRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.FAILED), any(Pageable.class)))
                .willReturn(List.of(failedEvent));
        CompletableFuture<SendResult<String, String>> sendFuture = CompletableFuture.completedFuture(null);
        given(waitingKafkaTemplate.send(any(String.class), any(String.class), any(String.class))).willReturn(sendFuture);

        WaitingOutboxPublisher publisher = new WaitingOutboxPublisher(
                waitingOutboxEventRepository,
                waitingKafkaTemplate,
                new WaitingKafkaProperties("waiting.events.v1", 1, (short) 1),
                new WaitingKafkaPublisherProperties(5000L, 60000L, 3000L, 100)
        );

        publisher.retryFailedEvents();

        assertThat(failedEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(failedEvent.getRetryCount()).isEqualTo(1);
        assertThat(failedEvent.getFailedReason()).isNull();
    }

    @Test
    @DisplayName("FAILED 이벤트는 생성 순으로 한 배치만 재시도한다")
    void retryFailedEvents_loadsFailedEventsBatch() {
        WaitingOutboxPublisher publisher = new WaitingOutboxPublisher(
                waitingOutboxEventRepository,
                waitingKafkaTemplate,
                new WaitingKafkaProperties("waiting.events.v1", 1, (short) 1),
                new WaitingKafkaPublisherProperties(5000L, 60000L, 3000L, 100)
        );

        given(waitingOutboxEventRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.FAILED), any(Pageable.class)))
                .willReturn(List.of());

        publisher.retryFailedEvents();

        org.mockito.BDDMockito.then(waitingOutboxEventRepository).should()
                .findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.FAILED), eq(PageRequest.of(0, 100)));
    }

    @Test
    @DisplayName("최대 재시도 횟수에 도달한 실패 이벤트는 DEAD_LETTER로 전이한다")
    void publishPendingEvents_deadLettersPoisonEvent() {
        WaitingOutboxEvent pendingEvent = WaitingOutboxEvent.builder()
                .waiting(waiting())
                .eventType(WaitingEventType.WAITING_REGISTERED)
                .payload("{\"waitingId\":\"test\"}")
                .build();

        given(waitingOutboxEventRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .willReturn(List.of(pendingEvent));
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("kafka down"));
        given(waitingKafkaTemplate.send(any(String.class), any(String.class), any(String.class))).willReturn(failedFuture);

        WaitingOutboxPublisher publisher = new WaitingOutboxPublisher(
                waitingOutboxEventRepository,
                waitingKafkaTemplate,
                new WaitingKafkaProperties("waiting.events.v1", 1, (short) 1),
                new WaitingKafkaPublisherProperties(5000L, 60000L, 3000L, 100)
        );

        publisher.publishPendingEvents();

        assertThat(pendingEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(pendingEvent.getRetryCount()).isEqualTo(1);
        assertThat(pendingEvent.getFailedReason()).isEqualTo("kafka down");
    }

    private Waiting waiting() {
        return Waiting.builder()
                .id(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .storeName("테스트 매장")
                .userId(UUID.randomUUID())
                .waitingNumber(1L)
                .peopleCount(2)
                .requestMessage(null)
                .build();
    }
}
