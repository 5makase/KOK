package com.omakase.kok.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 내부 API 외부 접근 차단 필터
 *
 * 정책:
 * - /api/v1/internal/** 경로는 외부 클라이언트가 직접 호출 불가
 * - 서비스 간 내부 호출 전용 경로
 * - 외부 접근 시 403 Forbidden 반환
 */
@Slf4j
@Component
@Order(-100) // Gateway 필터보다 먼저 실행
public class InternalBlockFilter implements WebFilter {

    private static final String INTERNAL_PATH_PREFIX = "/api/v1/internal";

    private final ObjectMapper objectMapper;

    public InternalBlockFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (path.equals(INTERNAL_PATH_PREFIX) || path.startsWith(INTERNAL_PATH_PREFIX + "/")) {
            log.warn("[Gateway] 내부 API 외부 접근 차단: {} {}",
                    exchange.getRequest().getMethod(), path);
            return writeErrorResponse(exchange, HttpStatus.FORBIDDEN, "[AUTH-006] 접근이 거부되었습니다.");
        }

        return chain.filter(exchange);
    }

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
            byte[] fallback = "{\"status\":403,\"message\":\"Forbidden\",\"data\":null}"
                    .getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(fallback);
            return response.writeWith(Mono.just(buffer));
        }
    }
}