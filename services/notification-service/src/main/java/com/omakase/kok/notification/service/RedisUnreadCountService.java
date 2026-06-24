package com.omakase.kok.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.OptionalLong;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisUnreadCountService {

    private static final String KEY_PREFIX = "user:";
    private static final String KEY_SUFFIX = ":unread_count";
    private static final Duration TTL = Duration.ofDays(1);

    private final StringRedisTemplate redisTemplate;

    public void increment(UUID userId) {
        redisTemplate.opsForValue().increment(buildKey(userId));
        redisTemplate.expire(buildKey(userId), TTL);
    }

    public void decrement(UUID userId) {
        String key = buildKey(userId);
        Long result = redisTemplate.opsForValue().decrement(key);
        if (result != null && result < 0) {
            redisTemplate.delete(key);
            log.warn("[RedisUnreadCountService] 미읽음 카운트 음수 감지, 캐시 제거. userId={}", userId);
        }
    }

    public void set(UUID userId, long count) {
        redisTemplate.opsForValue().set(buildKey(userId), String.valueOf(count), TTL);
    }

    public OptionalLong get(UUID userId) {
        String value = redisTemplate.opsForValue().get(buildKey(userId));
        if (value == null) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            log.warn("[RedisUnreadCountService] 미읽음 카운트 파싱 실패, 캐시 제거. userId={}, value={}", userId, value);
            redisTemplate.delete(buildKey(userId));
            return OptionalLong.empty();
        }
    }

    public void delete(UUID userId) {
        redisTemplate.delete(buildKey(userId));
    }

    private String buildKey(UUID userId) {
        return KEY_PREFIX + userId + KEY_SUFFIX;
    }
}
