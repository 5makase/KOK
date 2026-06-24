package com.omakase.kok.store.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI storeServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("KOK Store Service API")
                        .description("매장 등록, 조회, 수정, 삭제, 카테고리, 메뉴, 편의시설, 이미지, 랭킹 API")
                        .version("v1"))
                // 게이트웨이에서 JWT 검증 후 X-User-Id / X-Role 헤더로 전달 - 서비스 직접 호출 시 수동 입력
                .schemaRequirement("X-User-Id", headerScheme("X-User-Id"))
                .schemaRequirement("X-Role", headerScheme("X-Role"));
    }

    private SecurityScheme headerScheme(String headerName) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(headerName);
    }
}
