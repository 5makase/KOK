package com.omakase.kok.notification.service;

import com.omakase.kok.notification.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RedisIdempotencyService {

    private static final String KEY_PREFIX = "notification:idempotency:";

    private final StringRedisTemplate redisTemplate;

    @Value("${notification.idempotency.ttl-days:7}")
    private long ttlDays;

    /**
     * 처음 처리되는 이벤트면 Redis 키를 선점하고 true 반환.
     * 이미 처리된 이벤트면 false 반환 (중복).
     *
     * setIfAbsent (SET NX) 로 원자적으로 체크와 선점을 동시에 수행한다.
     */
    public boolean tryAcquire(UUID referenceId, NotificationType notificationType, UUID userId) {
        String key = buildKey(referenceId, notificationType, userId);
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofDays(ttlDays))
        );
    }

    private String buildKey(UUID referenceId, NotificationType notificationType, UUID userId) {
        return KEY_PREFIX + referenceId + ":" + notificationType + ":" + userId;
    }
}
