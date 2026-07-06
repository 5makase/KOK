package com.omakase.kok.aiops.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ChatConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        너는 SRE 보조 도구다. Prometheus 메트릭을 근거로만 답한다.
                        추측이 필요하면 '추측'이라고 명시하라. 근거 없는 단정은 금지한다.
                        메트릭이 없거나 부족하면 모른다고 답하라. 한국어로 간결하게 답하라.
                        """)
                .build();
    }
}
