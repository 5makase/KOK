package com.omakase.kok.user.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.user.infrastructure.security.JwtAuthenticationFilter;
import com.omakase.kok.user.infrastructure.security.JwtProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 인증 실패 시 401 반환을 담당하는 AuthenticationEntryPoint
    // JwtAuthenticationFilter에서 직접 401을 반환하지 않고 여기서 처리
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            com.omakase.kok.common.response.ApiResponse<Void> errorResponse =
                    com.omakase.kok.common.response.ApiResponse.fail(
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "[AUTH-001] 인증이 필요합니다."
                    );
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 인증 실패 시 AuthenticationEntryPoint에서 401 처리
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/users/signup",
                                "/api/v1/owners/signup",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/users/me",
                                "/actuator/health",
                                // Swagger
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().permitAll() // TODO: 개발 완료 후 authenticated()로 변경
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider, objectMapper),
                        UsernamePasswordAuthenticationFilter.class
                )
                .build();
    }
}