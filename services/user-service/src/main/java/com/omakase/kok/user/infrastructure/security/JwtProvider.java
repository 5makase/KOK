package com.omakase.kok.user.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 생성·검증 유틸리티
 *
 * 정책:
 * - Access Token  유효기간: 30분
 * - Refresh Token 유효기간: 7일
 * - Secret Key는 환경변수로 관리 (보안 주의사항 정책)
 * - 토큰은 로그에 출력하지 않음
 */
@Slf4j
@Component
public class JwtProvider {

    // 코드래빗 리뷰 반영: 토큰 타입 별 구분
    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration:1800000}")    // 기본 30분 (ms)
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:604800000}") // 기본 7일 (ms)
    private long refreshTokenExpiration;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ── Access Token ──────────────────────────────────────────────────────────

    /**
     * Access Token 생성
     * Payload: userId, username, role (정책 2.3)
     */
    public String generateAccessToken(String userId, String username, String role) {
        return buildToken(userId, username, role, TOKEN_TYPE_ACCESS , accessTokenExpiration);
    }

    // ── Refresh Token ─────────────────────────────────────────────────────────

    /**
     * Refresh Token 생성
     */
    public String generateRefreshToken(String userId, String username, String role) {
        return buildToken(userId, username, role, TOKEN_TYPE_REFRESH , refreshTokenExpiration);
    }

    // ── 검증 ──────────────────────────────────────────────────────────────────

    /**
     * 토큰 유효성 검증
     * - 서명 위조 / 만료 / 형식 오류 → false
     * 정책 6.2: 만료/위조 토큰은 모두 401로 처리
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("만료된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException | MalformedJwtException e) {
            log.debug("지원하지 않거나 형식이 잘못된 JWT 토큰입니다.");
        } catch (JwtException e) {
            log.debug("JWT 서명 검증에 실패하였습니다.");
        } catch (IllegalArgumentException e) {
            log.debug("JWT 토큰이 비어있습니다.");
        }
        return false;
    }

    // ── Claim 추출 ────────────────────────────────────────────────────────────

    public String extractUserId(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractUsername(String token) {
        return parseClaims(token).get("username", String.class);
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public String extractTokenType(String token) {
        return parseClaims(token).get(CLAIM_TOKEN_TYPE, String.class);
    }

    public boolean isRefreshToken(String token) {
        return TOKEN_TYPE_REFRESH.equals(extractTokenType(token));
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────

    private String buildToken(String userId, String username, String role, String tokenType, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("role", role)
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}