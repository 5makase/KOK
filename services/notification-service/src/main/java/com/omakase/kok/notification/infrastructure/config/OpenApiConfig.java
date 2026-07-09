package com.omakase.kok.notification.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationServiceOpenAPI() {
        return new OpenAPI()
                // 서버 URL 미지정 시 springdoc이 실제 요청 호스트(서비스 자체 IP:포트)를 자동 추론함 -
                // 실제 호출은 항상 Gateway를 거치므로 문서상 서버 주소도 Gateway로 고정
                .servers(List.of(new Server().url("http://localhost:8000").description("Gateway")))
                .info(new Info()
                        .title("KOK Notification Service API")
                        .description("알림 조회, 읽음 처리, 미읽음 카운트 API")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement()
                        .addList("X-User-Id"))
                .components(new Components()
                        .addSecuritySchemes("X-User-Id", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Id"))
                        .addSecuritySchemes("X-User-Role", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Role")));
    }
}
