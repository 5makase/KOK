package com.omakase.kok.notification.infrastructure.redis;

import com.omakase.kok.notification.domain.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RedisIdempotencyService {

    private static final String EVENT_KEY_PREFIX = "notification:idempotency:event:";
    private static final String BUSINESS_KEY_PREFIX = "notification:idempotency:";

    private final StringRedisTemplate redisTemplate;

    @Value("${notification.idempotency.ttl-days:7}")
    private long ttlDays;

    public boolean tryAcquireByEventId(UUID eventId) {
        String key = EVENT_KEY_PREFIX + eventId;
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofDays(ttlDays))
        );
    }

    public boolean tryAcquire(UUID referenceId, NotificationType notificationType, UUID userId) {
        String key = BUSINESS_KEY_PREFIX + referenceId + ":" + notificationType + ":" + userId;
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofDays(ttlDays))
        );
    }
}
