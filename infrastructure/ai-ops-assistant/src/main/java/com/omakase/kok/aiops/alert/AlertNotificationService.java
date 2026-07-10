package com.omakase.kok.aiops.alert;

import com.omakase.kok.aiops.slack.SlackClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 알람 분석 + Slack 통지 조율. Slack 발송 실패는 삼키고 분석 결과는 항상 반환한다
 */
@Slf4j
@Service
public class AlertNotificationService {

    private final AlertAnalyzer alertAnalyzer;
    private final SlackClient slackClient;
    private final String alertChannelId;

    public AlertNotificationService(
            AlertAnalyzer alertAnalyzer,
            SlackClient slackClient,
            @Value("${slack.alert-channel-id}") String alertChannelId
    ) {
        this.alertAnalyzer = alertAnalyzer;
        this.slackClient = slackClient;
        this.alertChannelId = alertChannelId;
    }

    public String analyzeAndNotify(String service) {
        String summary = alertAnalyzer.analyze(service);

        try {
            slackClient.postMessage(alertChannelId, "[%s] 알람 원인 분석\n%s".formatted(service, summary));
        } catch (Exception e) {
            log.warn("[AlertNotificationService] Slack 발송 실패: service={}", service, e);
        }

        return summary;
    }
}
