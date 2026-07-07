package com.omakase.kok.aiops.agent;

import com.omakase.kok.aiops.common.dto.ApiResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AgentController {

    private final AgentAssistant agentAssistant;

    @PostMapping("/api/agent/ask")
    public ApiResponse<String> ask(@RequestBody AgentRequest request) {
        return ApiResponse.success(agentAssistant.ask(request.getQuestion()));
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AgentRequest {
        private String question;
    }
}
