package com.omakase.kok.gateway.filter;

import com.omakase.kok.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@DisplayName("JwtAuthenticationFilter 단위 테스트 - 헤더 주입 방지")
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterUnitTest {

    @Mock
    private JwtUtil jwtUtil;

    @Test
    @DisplayName("외부 X-User-Id 헤더 주입 시도 → mutate()로 제거 후 JWT 기반 값으로 재설정")
    void prevent_xUserIdInjection() {
        // given
        UUID realUserId = UUID.randomUUID();
        UUID fakeUserId = UUID.randomUUID();

        Claims mockClaims = mock(Claims.class);

        // ✅ 실제로 호출되는 stubbing만 유지 (Claims.get() 제거)
        given(jwtUtil.parseClaims("validToken")).willReturn(mockClaims);
        given(jwtUtil.getUserId(mockClaims)).willReturn(realUserId.toString());
        given(jwtUtil.getUsername(mockClaims)).willReturn("testuser");
        given(jwtUtil.getRole(mockClaims)).willReturn("USER");

        // 공격자가 X-User-Id를 직접 헤더에 심은 요청
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/stores")
                .header("Authorization", "Bearer validToken")
                .header("X-User-Id", fakeUserId.toString())   // 주입 시도
                .header("X-Username", "attacker")             // 주입 시도
                .header("X-Role", "MASTER")                   // 권한 상승 시도
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when - Gateway의 JwtAuthenticationFilter 내부 mutate 로직 재현
        Claims claims = jwtUtil.parseClaims("validToken");

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-Username");
                    headers.remove("X-Role");
                })
                .header("X-User-Id", jwtUtil.getUserId(claims))
                .header("X-Username", jwtUtil.getUsername(claims))
                .header("X-Role", jwtUtil.getRole(claims))
                .build();

        // then
        assertThat(mutatedRequest.getHeaders().getFirst("X-User-Id"))
                .isEqualTo(realUserId.toString());
        assertThat(mutatedRequest.getHeaders().getFirst("X-User-Id"))
                .isNotEqualTo(fakeUserId.toString());

        assertThat(mutatedRequest.getHeaders().getFirst("X-Username"))
                .isEqualTo("testuser");
        assertThat(mutatedRequest.getHeaders().getFirst("X-Username"))
                .isNotEqualTo("attacker");

        assertThat(mutatedRequest.getHeaders().getFirst("X-Role"))
                .isEqualTo("USER");
        assertThat(mutatedRequest.getHeaders().getFirst("X-Role"))
                .isNotEqualTo("MASTER");
    }
}