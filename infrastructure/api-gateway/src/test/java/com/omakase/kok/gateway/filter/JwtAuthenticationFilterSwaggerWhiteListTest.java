package com.omakase.kok.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.gateway.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * Swagger 문서/정적 리소스 경로(/v3/api-docs/**, /swagger-ui/**, /webjars/**)와
 * /actuator/health가 Authorization 헤더 없이도 통과하는지(JWT 검증을 타지 않는지) 검증한다.
 */
@DisplayName("JwtAuthenticationFilter - Swagger GET 화이트리스트 테스트")
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterSwaggerWhiteListTest {

    @Mock
    private JwtUtil jwtUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GatewayFilter filter() {
        return new JwtAuthenticationFilter(jwtUtil, objectMapper)
                .apply(new JwtAuthenticationFilter.Config());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/v3/api-docs/user-service",
            "/v3/api-docs",
            "/swagger-ui.html",
            "/swagger-ui/index.html",
            "/webjars/swagger-ui/swagger-ui.css",
            "/actuator/health"
    })
    @DisplayName("성공 - Authorization 헤더 없이도 통과하고 JwtUtil은 호출되지 않는다")
    void passesThrough_withoutAuthorizationHeader(String path) {
        // given
        MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        given(chain.filter(any())).willReturn(Mono.empty());

        // when
        filter().filter(exchange, chain).block();

        // then
        then(chain).should().filter(any());
        then(jwtUtil).should(never()).parseClaims(any());
    }

    @Test
    @DisplayName("실패 - 화이트리스트에 없는 GET 요청은 Authorization 헤더가 없으면 401을 반환한다")
    void blocksNonWhiteListedPath_withoutAuthorizationHeader() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        // when
        filter().filter(exchange, chain).block();

        // then
        then(chain).should(never()).filter(any());
    }
}