package com.omakase.kok.waiting.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI waitingServiceOpenAPI() {
        return new OpenAPI()
                // 서버 URL 미지정 시 springdoc이 실제 요청 호스트(서비스 자체 IP:포트)를 자동 추론함 -
                // 실제 호출은 항상 Gateway를 거치므로 문서상 서버 주소도 Gateway로 고정
                .servers(List.of(new Server().url("http://localhost:8000").description("Gateway")))
                .info(new Info()
                        .title("KOK Waiting Service API")
                        .description("웨이팅 등록, 조회, 호출, 입장, 취소 및 매장 웨이팅 설정 API")
                        .version("v1"))
                .schemaRequirement("Bearer Authentication", bearerToken());
    }

    private SecurityScheme bearerToken() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");
    }
}
