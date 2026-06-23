package com.omakase.kok.user.infrastructure.security;

import java.util.Optional;

/**
 * Refresh Token 저장소 인터페이스
 * 정책 5.2: MVP 기준 Redis 사용
 */
public interface RefreshTokenStore {

    void save(String userId, String refreshToken);

    Optional<String> findByUserId(String userId);

    void deleteByUserId(String userId);
}