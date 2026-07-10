package com.omakase.kok.user.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.common.response.ApiResponse;
import com.omakase.kok.user.global.exception.UserErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 검증 필터
 *
 * user-service는 Gateway로부터 X-User-Id, X-Role 헤더를 받아 인증 처리하는 구조이나,
 * 로그인·재발급 API는 Gateway를 통과하지 않고 직접 JWT를 검증한다.
 *
 * 처리 흐름:
 * 1. Authorization 헤더에서 Bearer 토큰 추출
 * 2. JwtProvider로 토큰 유효성 검증
 * 3. 검증 실패 시 즉시 401 반환 → SecurityContext 비워둔 채 다음 필터로 통과
 * permitAll 경로는 그대로 통과, protected 경로는 인가 레이어(AuthenticationEntryPoint)에서 401 처리
 * 4. SecurityContext에 인증 정보 등록
 * 5. 검증 실패 시 401 응답 반환
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        // Authorization 헤더가 없거나 Bearer 형식이 아니면 다음 필터로 통과
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        // 토큰 유효성 검증 실패 → 401 반환
        if (!jwtProvider.validateToken(token)) {
            sendErrorResponse(response, UserErrorCode.INVALID_TOKEN);
            return;
        }

        // 토큰 검증 실패 시 즉시 401 반환하지 않고 다음 필터로 통과
        //         SecurityContext가 비어있으므로
        //         - permitAll 경로 → 인증 없이 그대로 통과
        //         - protected 경로 → 인가 레이어에서 AuthenticationEntryPoint가 401 반환
        if (!jwtProvider.validateToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // SecurityContext에 인증 정보 등록
        String userId = jwtProvider.extractUserId(token);
        String role = jwtProvider.extractRole(token);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, UserErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> errorResponse = ApiResponse.fail(
                errorCode.getStatus().value(),
                String.format("[%s] %s", errorCode.getCode(), errorCode.getMessage())
        );
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}