package com.omakase.kok.aiops.alert;

import com.omakase.kok.aiops.common.dto.ApiResponse;
import com.omakase.kok.aiops.slack.SlackClient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AlertController {

    private final AlertAnalyzer alertAnalyzer;
    private final SlackClient slackClient;

    @Value("${slack.alert-channel-id}")
    private String alertChannelId;

    @PostMapping("/api/alerts/analyze")
    public ApiResponse<String> analyze(@RequestBody AlertRequest request) {
        String summary = alertAnalyzer.analyze(request.getService());
        slackClient.postMessage(alertChannelId, "[%s] 알람 원인 분석\n%s".formatted(request.getService(), summary));
        return ApiResponse.success(summary);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AlertRequest {
        private String service;
    }
}
