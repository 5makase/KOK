package com.omakase.kok.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaConfig {

    // Spring Security 없이 헤더(X-User-Id)로 현재 사용자를 식별
    // @CreatedBy, @LastModifiedBy 자동 주입에 사용됨
    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return () -> {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return Optional.empty();
            String userId = attrs.getRequest().getHeader("X-User-Id");
            if (userId == null || userId.isBlank()) return Optional.empty();
            try {
                return Optional.of(UUID.fromString(userId));
            } catch (IllegalArgumentException e) {
                // 게이트웨이 우회 또는 설정 오류로 X-User-Id가 UUID 형식이 아님 -> 감사 필드 null 처리
                log.warn("X-User-Id 헤더가 유효한 UUID 형식이 아닙니다: {}", userId);
                return Optional.empty();
            }
        };
    }
}