package com.omakase.kok.waiting.infrastructure.messaging;

import com.omakase.kok.waiting.domain.entity.Waiting;
import com.omakase.kok.waiting.domain.enums.WaitingEventType;
import com.omakase.kok.waiting.infrastructure.messaging.dto.WaitingCalledEvent;
import com.omakase.kok.waiting.infrastructure.messaging.dto.WaitingCancelledEvent;
import com.omakase.kok.waiting.infrastructure.messaging.dto.WaitingEnteredEvent;
import com.omakase.kok.waiting.infrastructure.messaging.dto.WaitingEventEnvelope;
import com.omakase.kok.waiting.infrastructure.messaging.dto.WaitingNoShowEvent;
import com.omakase.kok.waiting.infrastructure.messaging.dto.WaitingRegisteredEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class WaitingEventFactory {
    private static final int SCHEMA_VERSION = 1;
    private static final String PRODUCER = "waiting-service";

    // 웨이팅 등록
    public WaitingEventEnvelope<WaitingRegisteredEvent> createRegisteredEnvelope(
            UUID eventId,
            Waiting waiting,
            Long currentRank
    ) {
        WaitingRegisteredEvent payload = new WaitingRegisteredEvent(
                waiting.getId(),
                waiting.getStoreId(),
                waiting.getStoreName(),
                waiting.getUserId(),
                waiting.getVisitorName(),
                waiting.getWaitingNumber(),
                waiting.getPeopleCount(),
                currentRank
        );
        return createEnvelope(eventId, WaitingEventType.WAITING_REGISTERED, waiting.getCreatedAt(), payload);
    }

    // 웨이팅 호출
    public WaitingEventEnvelope<WaitingCalledEvent> createCalledEnvelope(
            UUID eventId,
            Waiting waiting,
            Integer callTimeoutMinutes
    ) {
        WaitingCalledEvent payload = new WaitingCalledEvent(
                waiting.getId(),
                waiting.getStoreId(),
                waiting.getStoreName(),
                waiting.getUserId(),
                waiting.getVisitorName(),
                waiting.getWaitingNumber(),
                callTimeoutMinutes
        );
        return createEnvelope(eventId, WaitingEventType.WAITING_CALLED, waiting.getCalledAt(), payload);
    }

    // 웨이팅 입장
    public WaitingEventEnvelope<WaitingEnteredEvent> createEnteredEnvelope(UUID eventId, Waiting waiting) {
        WaitingEnteredEvent payload = new WaitingEnteredEvent(
                waiting.getId(),
                waiting.getStoreId(),
                waiting.getStoreName(),
                waiting.getUserId(),
                waiting.getVisitorName(),
                waiting.getWaitingNumber()
        );
        return createEnvelope(eventId, WaitingEventType.WAITING_ENTERED, waiting.getEnteredAt(), payload);
    }

    // 웨이팅 취소
    public WaitingEventEnvelope<WaitingCancelledEvent> createCancelledEnvelope(UUID eventId, Waiting waiting) {
        WaitingCancelledEvent payload = new WaitingCancelledEvent(
                waiting.getId(),
                waiting.getStoreId(),
                waiting.getStoreName(),
                waiting.getUserId(),
                waiting.getVisitorName(),
                waiting.getWaitingNumber(),
                waiting.getCancelReason()
        );
        return createEnvelope(eventId, WaitingEventType.WAITING_CANCELLED, waiting.getCancelledAt(), payload);
    }

    // 웨이팅 미입장
    public WaitingEventEnvelope<WaitingNoShowEvent> createNoShowEnvelope(UUID eventId, Waiting waiting) {
        WaitingNoShowEvent payload = new WaitingNoShowEvent(
                waiting.getId(),
                waiting.getStoreId(),
                waiting.getStoreName(),
                waiting.getUserId(),
                waiting.getVisitorName(),
                waiting.getWaitingNumber(),
                waiting.getNoShowReason()
        );
        return createEnvelope(eventId, WaitingEventType.WAITING_NO_SHOW, waiting.getNoShowedAt(), payload);
    }

    private <T> WaitingEventEnvelope<T> createEnvelope(
            UUID eventId,
            WaitingEventType eventType,
            LocalDateTime occurredAt,
            T payload
    ) {
        return new WaitingEventEnvelope<>(
                eventId,
                eventType,
                SCHEMA_VERSION,
                occurredAt,
                PRODUCER,
                payload
        );
    }
}
