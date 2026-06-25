package com.omakase.kok.notification.event;

import com.omakase.kok.notification.enums.ReferenceType;

import java.util.Map;
import java.util.UUID;

/**
 * Kafka 이벤트의 producer 필드를 기반으로 ReferenceType과 referenceId를 결정한다.
 *
 * 팀 표준 Envelope에 referenceType/referenceId가 없으므로,
 * notification-service 내부에서 producer 값으로 매핑한다.
 *
 * producer 추가 시 두 Map 모두 업데이트 필요.
 */
public final class ProducerEventMapper {

    private static final Map<String, ReferenceType> PRODUCER_TO_REFERENCE_TYPE = Map.of(
            "waiting-service",     ReferenceType.WAITING,
            "reservation-service", ReferenceType.RESERVATION,
            "payment-service",     ReferenceType.PAYMENT,
            "review-service",      ReferenceType.REVIEW,
            "user-service",        ReferenceType.USER
    );

    // payload 안에서 도메인 기본 ID 필드명
    private static final Map<String, String> PRODUCER_TO_ID_FIELD = Map.of(
            "waiting-service",     "waitingId",
            "reservation-service", "reservationId",
            "payment-service",     "paymentId",
            "review-service",      "reviewId",
            "user-service",        "userId"
    );

    private ProducerEventMapper() {}

    public static ReferenceType toReferenceType(String producer) {
        ReferenceType type = PRODUCER_TO_REFERENCE_TYPE.get(producer);
        if (type == null) {
            throw new IllegalArgumentException("알 수 없는 producer: " + producer);
        }
        return type;
    }

    public static UUID toReferenceId(String producer, Map<String, Object> payload) {
        String fieldName = PRODUCER_TO_ID_FIELD.get(producer);
        if (fieldName == null) {
            throw new IllegalArgumentException("알 수 없는 producer: " + producer);
        }
        Object id = payload.get(fieldName);
        if (id == null) {
            throw new IllegalArgumentException("payload에 '" + fieldName + "' 필드가 없습니다.");
        }
        return UUID.fromString(id.toString());
    }

    public static UUID toUserId(Map<String, Object> payload) {
        Object userId = payload.get("userId");
        if (userId == null) {
            throw new IllegalArgumentException("payload에 'userId' 필드가 없습니다.");
        }
        return UUID.fromString(userId.toString());
    }
}
