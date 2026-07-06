package com.omakase.kok.aiops.slack;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 지정된 채널에 알람 분석 결과를 게시하는 Slack 봇 클라이언트 (chat.postMessage만 지원)
 * notification-service의 SlackClient와는 목적이 달라 독립적으로 구현함
 * 운영자용 채널 공지가 전부라 사용자 DM 조회는 불필요
 */
@Slf4j
@Component
public class SlackClient {

    private static final String CHAT_POST_MESSAGE_URL = "https://slack.com/api/chat.postMessage";

    private final RestTemplate restTemplate;
    private final String botToken;

    public SlackClient(RestTemplate restTemplate, @Value("${slack.bot-token}") String botToken) {
        this.restTemplate = restTemplate;
        this.botToken = botToken;
    }

    public void postMessage(String channelId, String message) {
        HttpEntity<Map<String, String>> request = new HttpEntity<>(
                Map.of("channel", channelId, "text", escapeSlackText(message)),
                createAuthHeaders()
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                CHAT_POST_MESSAGE_URL, HttpMethod.POST, request, Map.class
        );

        Map<String, Object> body = response.getBody();
        if (body == null || !Boolean.TRUE.equals(body.get("ok"))) {
            throw new IllegalStateException("chat.postMessage 실패: error=" + (body != null ? body.get("error") : "null"));
        }
    }

    private String escapeSlackText(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(botToken);
        return headers;
    }
}
