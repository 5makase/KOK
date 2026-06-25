package com.omakase.kok.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 인증 필터
 *
 * 정책:
 * - 인증 제외 경로는 JWT 검증 없이 통과
 * - 그 외 모든 요청은 Bearer Token 검증 필수
 * - 검증 성공 시 X-User-Id, X-Username, X-Role 헤더를 내부 서비스로 전달
 * - 외부에서 주입된 헤더는 application.yml default-filters에서 이미 제거됨
 * - 토큰은 로그에 출력하지 않음 (보안 정책)
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    // 인증 제외 경로 (정책 3.3)
    private static final List<String> WHITE_LIST = List.of(
            "/api/v1/users/signup",
            "/api/v1/owners/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/reissue",
            "/actuator/health"
    );

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().value();
            String method = request.getMethod().name();

            // 인증 제외 경로 확인
            if (isWhiteListed(path, method)) {
                log.debug("[Gateway] 인증 제외 경로 통과: {} {}", method, path);
                return chain.filter(exchange);
            }

            // Authorization Header 확인
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || authHeader.isBlank()) {
                log.warn("[Gateway] Authorization 헤더 없음: {} {}", method, path);
                return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "[AUTH-001] Authorization 헤더가 없습니다.");
            }

            if (!authHeader.startsWith("Bearer ")) {
                log.warn("[Gateway] Bearer 형식이 아님: {} {}", method, path);
                return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "[AUTH-002] Bearer 형식의 토큰이 아닙니다.");
            }

            // Bearer 토큰 추출 (토큰 값은 로그에 출력하지 않음 - 보안 정책)
            String token = authHeader.substring(7);

            try {
                Claims claims = jwtUtil.parseClaims(token);

                String userId = jwtUtil.getUserId(claims);
                String username = jwtUtil.getUsername(claims);
                String role = jwtUtil.getRole(claims);

                if (userId == null || username == null || role == null) {
                    log.warn("[Gateway] 토큰 Claims 누락: {} {}", method, path);
                    return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "[AUTH-005] 토큰 형식이 올바르지 않습니다.");
                }

                try {
                    UUID.fromString(userId);
                } catch (IllegalArgumentException e) {
                    log.warn("[Gateway] userId UUID 형식 오류: {} {}", method, path);
                    return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "[AUTH-005] 토큰 형식이 올바르지 않습니다.");
                }

                log.debug("[Gateway] 인증 성공 - userId: {}, role: {}, path: {} {}", userId, role, method, path);

                // 검증된 사용자 정보를 헤더로 내부 서비스에 전달 (정책 3.4)
                // 외부에서 주입된 헤더 제거 후 JWT 기반 값으로 재설정 (헤더 인젝션 방지)
                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                        .headers(headers -> {
                            headers.remove("X-User-Id");
                            headers.remove("X-Username");
                            headers.remove("X-Role");
                            headers.remove("X-User-Role");
                            headers.add("X-User-Id", userId);
                            headers.add("X-Username", username);
                            headers.add("X-Role", role);
                        })
                        .build();

                return chain.filter(exchange.mutate().request(mutatedRequest).build());

            } catch (ExpiredJwtException e) {
                log.warn("[Gateway] 만료된 토큰: {} {}", method, path);
                return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "[AUTH-003] 만료된 토큰입니다.");
            } catch (JwtException e) {
                log.warn("[Gateway] 위조된 토큰: {} {}", method, path);
                return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "[AUTH-004] 유효하지 않은 토큰입니다.");
            } catch (IllegalArgumentException e) {
                log.warn("[Gateway] 잘못된 토큰 형식: {} {}", method, path);
                return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "[AUTH-005] 토큰 형식이 올바르지 않습니다.");
            }
        };
    }

    /**
     * 인증 제외 경로 확인
     */
    private boolean isWhiteListed(String path, String method) {
        // POST 메서드 기반 정확한 경로 매핑
        if (method.equals("POST") && WHITE_LIST.stream()
                .anyMatch(whitePath -> pathMatcher.match(whitePath, path))) {
            return true;
        }
        // GET /actuator/health
        if (method.equals("GET") && pathMatcher.match("/actuator/health", path)) {
            return true;
        }
        return false;
    }

    /**
     * 에러 응답 작성 - 공통 API 응답 포맷 준수 (정책 6.3)
     */
    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("message", message);
        body.put("data", null);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            byte[] fallback = "{\"status\":500,\"message\":\"Internal Server Error\",\"data\":null}"
                    .getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(fallback);
            return response.writeWith(Mono.just(buffer));
        }
    }

    public static class Config {
        // 현재 설정값 없음 - 추후 확장 가능
    }
}