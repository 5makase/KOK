package com.kok.review.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI reviewServiceOpenAPI() {
        return new OpenAPI()
                // 서버 URL 미지정 시 springdoc이 실제 요청 호스트(서비스 자체 IP:포트)를 자동 추론함 -
                // 실제 호출은 항상 Gateway를 거치므로 문서상 서버 주소도 Gateway로 고정
                .servers(List.of(new Server().url("http://localhost:8000").description("Gateway")))
                .info(new Info()
                        .title("KOK Review Service API")
                        .description("리뷰 CRUD, 신고, 사장님 답글 API")
                        .version("v1"));
    }
}
