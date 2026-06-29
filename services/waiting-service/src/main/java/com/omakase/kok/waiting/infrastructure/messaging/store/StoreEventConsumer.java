package com.omakase.kok.waiting.infrastructure.messaging.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.waiting.application.service.WaitingSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoreEventConsumer {
    private final WaitingSettingService waitingSettingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${waiting.kafka.topic.store-events.name}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) {
        if (message == null || message.isBlank()) {
            log.warn("store.events.v1 null/blank 메시지 무시");
            return;
        }
        try {
            StoreEvent event = objectMapper.readValue(message, StoreEvent.class);

            StoreEventType eventType = event.getEventType();
            if (eventType == null) {
                log.warn("처리할 수 없는 store eventType. eventType={}, eventId={}", event.getEventType(), event.getEventId());
                return;
            }

            StoreEvent.Payload payload = event.getPayload();
            if (event.getSchemaVersion() == null || payload == null || payload.getStoreId() == null) {
                log.warn("필수 필드 누락된 store 이벤트 무시. eventId={}", event.getEventId());
                return;
            }

            if (eventType == StoreEventType.STORE_CREATED) {
                var response = waitingSettingService.initializeDefaultWaitingSetting(payload.getStoreId());
                log.info("매장 생성 이벤트 기반 웨이팅 설정 초기화 완료. storeId={}, settingCreated={}",
                        payload.getStoreId(), response.getSettingCreated());
            }
        } catch (JsonProcessingException e) {
            log.warn("store.events.v1 역직렬화 실패 — 메시지 버림 (raw payload omitted)", e);
        } catch (BaseException e) {
            log.warn("store.events.v1 처리 불가 — 비즈니스 예외. error={}", e.getMessage());
        } catch (Exception e) {
            log.error("store.events.v1 처리 실패. error={}", e.getMessage(), e);
            throw new RuntimeException("store.events.v1 처리 실패 — Kafka 재처리 유도", e);
        }
    }
}
