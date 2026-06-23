package com.omakase.kok.user.infrastructure.persistence;

import com.omakase.kok.user.infrastructure.security.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 Refresh Token 저장소
 *
 * Key 형식 : refresh:token:{userId}
 * TTL      : Refresh Token 유효기간과 동일 (자동 만료)
 */
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:token:";

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.refresh-token-expiration:604800000}") // 기본 7일 (ms)
    private long refreshTokenExpirationMs;

    @Override
    public void save(String userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                buildKey(userId),
                refreshToken,
                refreshTokenExpirationMs,
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public Optional<String> findByUserId(String userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(buildKey(userId)));
    }

    @Override
    public void deleteByUserId(String userId) {
        redisTemplate.delete(buildKey(userId));
    }

    private String buildKey(String userId) {
        return KEY_PREFIX + userId;
    }
}