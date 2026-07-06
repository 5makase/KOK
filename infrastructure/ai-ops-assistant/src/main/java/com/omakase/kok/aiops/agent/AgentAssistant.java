package com.omakase.kok.aiops.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Tool Calling. ChatClient에 PrometheusTools를 붙여 LLM이 질문에 답하기 위해 필요한 도구를 스스로 골라 여러 번 호출한 뒤 응답 구성
 */
@Service
@RequiredArgsConstructor
public class AgentAssistant {

    private final ChatClient chatClient;
    private final PrometheusTools tools;

    public String ask(String question) {
        return chatClient.prompt()
                .system("""
                        너는 SRE 보조다. 답하기 위해 메트릭이 필요하면 제공된 도구를 사용하라.
                        여러 번 조회해도 좋다. 모든 결론은 조회한 수치를 근거로 제시하라.
                        job 레이블로 서비스를 구분한다 (service 레이블은 없다).
                        """)
                .user(question)
                .tools(tools)
                .call()
                .content();
    }
}
