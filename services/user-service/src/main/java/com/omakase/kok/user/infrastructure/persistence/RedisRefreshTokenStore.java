package com.omakase.kok.user.infrastructure.persistence;

import com.omakase.kok.user.infrastructure.security.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 Refresh Token 저장소
 *
 * Key 형식 : refresh:token:{userId}
 * TTL      : Refresh Token 유효기간과 동일 (자동 만료)
 *
 * 보안: 토큰 원문 대신 SHA-256 다이제스트를 저장한다.
 * Redis가 침해되더라도 토큰 원문을 복원할 수 없다.
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
                digest(refreshToken),
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

    /**
     * 토큰 저장·비교 시 항상 이 메서드를 통해 변환한다.
     */
    @Override
    public String digest(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 Java 표준 알고리즘이므로 실질적으로 발생하지 않음
            throw new IllegalStateException("SHA-256 알고리즘을 찾을 수 없습니다.", e);
        }
    }

    private String buildKey(String userId) {
        return KEY_PREFIX + userId;
    }
}