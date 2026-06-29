package com.omakase.kok.waiting.infrastructure.messaging.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.common.exception.CommonErrorCode;
import com.omakase.kok.waiting.application.service.WaitingSettingService;
import com.omakase.kok.waiting.domain.entity.WaitingSetting;
import com.omakase.kok.waiting.presentation.dto.response.WaitingSettingInitializeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StoreEventConsumerTest {
    @Mock
    private WaitingSettingService waitingSettingService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private StoreEventConsumer storeEventConsumer;

    private UUID storeId;
    private String validMessage;

    @BeforeEach
    void setUp() {
        storeId = UUID.randomUUID();
        validMessage = """
                {
                  "eventId": "%s",
                  "eventType": "STORE_CREATED",
                  "schemaVersion": 1,
                  "occurredAt": "2026-06-29T13:40:00",
                  "producer": "store-service",
                  "payload": {
                    "storeId": "%s"
                  }
                }
                """.formatted(UUID.randomUUID(), storeId);
    }

    @Test
    @DisplayName("STORE_CREATED 이벤트 수신 시 기본 웨이팅 설정을 초기화한다")
    void consume_storeCreated_initializesDefaultWaitingSetting() {
        given(waitingSettingService.initializeDefaultWaitingSetting(storeId))
                .willReturn(WaitingSettingInitializeResponse.of(WaitingSetting.create(storeId, null, null, null, null, null), true));

        storeEventConsumer.consume(validMessage);

        verify(waitingSettingService).initializeDefaultWaitingSetting(storeId);
    }

    @Test
    @DisplayName("null 메시지는 무시한다")
    void consume_nullMessage_skips() {
        storeEventConsumer.consume(null);

        verify(waitingSettingService, never()).initializeDefaultWaitingSetting(any());
    }

    @Test
    @DisplayName("blank 메시지는 무시한다")
    void consume_blankMessage_skips() {
        storeEventConsumer.consume("   ");

        verify(waitingSettingService, never()).initializeDefaultWaitingSetting(any());
    }

    @Test
    @DisplayName("역직렬화 실패는 예외를 전파하지 않는다")
    void consume_invalidJson_doesNotThrow() {
        assertThatCode(() -> storeEventConsumer.consume("invalid json"))
                .doesNotThrowAnyException();

        verify(waitingSettingService, never()).initializeDefaultWaitingSetting(any());
    }

    @Test
    @DisplayName("알 수 없는 이벤트 타입은 무시한다")
    void consume_unknownEventType_skips() {
        storeEventConsumer.consume(validMessage.replace("STORE_CREATED", "STORE_UPDATED"));

        verify(waitingSettingService, never()).initializeDefaultWaitingSetting(any());
    }

    @Test
    @DisplayName("필수 필드가 누락된 이벤트는 무시한다")
    void consume_missingStoreId_skips() {
        String missingStoreIdMessage = """
                {
                  "eventId": "%s",
                  "eventType": "STORE_CREATED",
                  "schemaVersion": 1,
                  "occurredAt": "2026-06-29T13:40:00",
                  "producer": "store-service",
                  "payload": {}
                }
                """.formatted(UUID.randomUUID());

        storeEventConsumer.consume(missingStoreIdMessage);

        verify(waitingSettingService, never()).initializeDefaultWaitingSetting(any());
    }

    @Test
    @DisplayName("비즈니스 예외는 예외를 전파하지 않는다")
    void consume_baseException_doesNotThrow() {
        doThrow(new BaseException(CommonErrorCode.INTERNAL_SERVER_ERROR))
                .when(waitingSettingService).initializeDefaultWaitingSetting(any());

        assertThatCode(() -> storeEventConsumer.consume(validMessage))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("일반 예외는 Kafka 재처리를 위해 RuntimeException으로 전파한다")
    void consume_runtimeException_propagates() {
        doThrow(new RuntimeException("DB down"))
                .when(waitingSettingService).initializeDefaultWaitingSetting(any());

        assertThatThrownBy(() -> storeEventConsumer.consume(validMessage))
                .isInstanceOf(RuntimeException.class);
    }
}
