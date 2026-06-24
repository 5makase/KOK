package com.omakase.kok.waiting.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI waitingServiceOpenAPI() {
        return new OpenAPI()
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
