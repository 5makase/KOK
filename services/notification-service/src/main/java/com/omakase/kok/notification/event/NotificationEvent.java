package com.omakase.kok.notification.event;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka 이벤트 공통 페이로드
 *
 * 발행 서비스는 아래 필드를 반드시 포함해야 한다.
 * - eventType    : NotificationType enum 값 (ex. WAITING_CALLED)
 * - userId       : 수신자 ID
 * - referenceId  : 원본 데이터 ID (waitingId / reservationId 등을 referenceId로 표준화)
 * - referenceType: 원본 타입 (WAITING / RESERVATION / PAYMENT / REVIEW / USER)
 *
 * 그 외 필드(storeName, waitingNumber 등)는 params 에 자동 수집되어
 * NotificationType.render() 에 전달된다.
 */
@Getter
@NoArgsConstructor
public class NotificationEvent {

    private String eventType;
    private UUID userId;
    private UUID referenceId;
    private String referenceType;

    private final Map<String, Object> params = new HashMap<>();

    @JsonAnySetter
    public void addParam(String key, Object value) {
        this.params.put(key, value);
    }
}
