package com.omakase.kok.store.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.common.exception.CommonErrorCode;
import com.omakase.kok.store.application.StoreRatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewEventConsumerTest {

    @Mock StoreRatingService storeRatingService;
    @Spy ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    ReviewEventConsumer reviewEventConsumer;

    private String validMessage;
    private UUID storeId;

    @BeforeEach
    void setUp() {
        storeId = UUID.randomUUID();
        validMessage = """
                {
                  "eventId": "%s",
                  "eventType": "REVIEW_CREATED",
                  "schemaVersion": 1,
                  "occurredAt": "2024-01-01T00:00:00",
                  "producer": "review-service",
                  "payload": {
                    "reviewId": "%s",
                    "storeId": "%s",
                    "rating": 4.5,
                    "averageRating": 4.3,
                    "reviewCount": 10
                  }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), storeId);
    }

    @Test
    @DisplayName("정상 이벤트 - updateRating() 호출")
    void consume_valid_event_calls_update_rating() {
        reviewEventConsumer.consume(validMessage);

        verify(storeRatingService).updateRating(
                eq(storeId),
                argThat(v -> v.compareTo(new BigDecimal("4.3")) == 0),
                eq(10)
        );
    }

    @Test
    @DisplayName("null 메시지 - updateRating() 미호출")
    void consume_null_message_skips() {
        reviewEventConsumer.consume(null);

        verify(storeRatingService, never()).updateRating(any(), any(), any());
    }

    @Test
    @DisplayName("blank 메시지 - updateRating() 미호출")
    void consume_blank_message_skips() {
        reviewEventConsumer.consume("   ");

        verify(storeRatingService, never()).updateRating(any(), any(), any());
    }

    @Test
    @DisplayName("역직렬화 실패 - 예외 미전파 (poison pill 처리)")
    void consume_invalid_json_does_not_throw() {
        assertThatCode(() -> reviewEventConsumer.consume("invalid json"))
                .doesNotThrowAnyException();

        verify(storeRatingService, never()).updateRating(any(), any(), any());
    }

    @Test
    @DisplayName("알 수 없는 eventType - updateRating() 미호출")
    void consume_unknown_event_type_skips() {
        String unknownTypeMessage = validMessage.replace("REVIEW_CREATED", "UNKNOWN_TYPE");

        reviewEventConsumer.consume(unknownTypeMessage);

        verify(storeRatingService, never()).updateRating(any(), any(), any());
    }

    @Test
    @DisplayName("필수 필드 누락 (storeId null) - updateRating() 미호출")
    void consume_missing_required_field_skips() {
        String missingFieldMessage = """
                {
                  "eventId": "%s",
                  "eventType": "REVIEW_CREATED",
                  "schemaVersion": 1,
                  "occurredAt": "2024-01-01T00:00:00",
                  "producer": "review-service",
                  "payload": {
                    "reviewId": "%s",
                    "rating": 4.5,
                    "averageRating": 4.3,
                    "reviewCount": 10
                  }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        reviewEventConsumer.consume(missingFieldMessage);

        verify(storeRatingService, never()).updateRating(any(), any(), any());
    }

    @Test
    @DisplayName("reviewCount 음수 - updateRating() 미호출")
    void consume_negative_review_count_skips() {
        String negativeCountMessage = validMessage.replace("\"reviewCount\": 10", "\"reviewCount\": -1");

        reviewEventConsumer.consume(negativeCountMessage);

        verify(storeRatingService, never()).updateRating(any(), any(), any());
    }

    @Test
    @DisplayName("BaseException 발생 - 예외 미전파 (비즈니스 예외 poison pill 처리)")
    void consume_base_exception_does_not_throw() {
        doThrow(new BaseException(CommonErrorCode.INTERNAL_SERVER_ERROR))
                .when(storeRatingService).updateRating(any(), any(), any());

        assertThatCode(() -> reviewEventConsumer.consume(validMessage))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("일반 예외 발생 - RuntimeException 전파로 Kafka 재처리 유도")
    void consume_runtime_exception_propagates_for_kafka_retry() {
        doThrow(new RuntimeException("DB 연결 실패"))
                .when(storeRatingService).updateRating(any(), any(), any());

        assertThatThrownBy(() -> reviewEventConsumer.consume(validMessage))
                .isInstanceOf(RuntimeException.class);
    }
}
