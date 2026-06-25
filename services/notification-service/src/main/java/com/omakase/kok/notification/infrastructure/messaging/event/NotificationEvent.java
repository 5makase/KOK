package com.omakase.kok.notification.infrastructure.messaging.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka 이벤트 공통 Envelope.
 *
 * 팀 표준 구조:
 * - eventId      : 이벤트 고유 ID
 * - eventType    : NotificationType enum 값 (ex. WAITING_REGISTERED)
 * - schemaVersion: 스키마 버전
 * - occurredAt   : 이벤트 발생 일시 (ISO 8601)
 * - producer     : 발행 서비스명 (ex. waiting-service) → ReferenceType 매핑에 사용
 * - payload      : 도메인별 데이터 스냅샷
 *                  → userId, 도메인 ID(waitingId 등) 포함 필수
 *                  → NotificationType.render() 에 그대로 전달됨
 */
@Getter
@NoArgsConstructor
public class NotificationEvent {

    private UUID eventId;
    private String eventType;
    private Integer schemaVersion;
    private String occurredAt;
    private String producer;

    @JsonProperty("payload")
    private Map<String, Object> payload = new HashMap<>();
}
