package com.omakase.kok.notification.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Slf4j
@Component
public class SlackClient {

    private static final String CONVERSATIONS_OPEN_URL = "https://slack.com/api/conversations.open";
    private static final String CHAT_POST_MESSAGE_URL = "https://slack.com/api/chat.postMessage";
    private static final String USERS_LOOKUP_BY_EMAIL_URL = "https://slack.com/api/users.lookupByEmail";

    private final RestTemplate restTemplate;
    private final String botToken;

    public SlackClient(RestTemplate restTemplate, @Value("${slack.bot-token}") String botToken) {
        this.restTemplate = restTemplate;
        this.botToken = botToken;
    }

    @SuppressWarnings("unchecked")
    public String lookupByEmail(String email) {
        HttpEntity<Void> request = new HttpEntity<>(createAuthHeaders());

        String url = UriComponentsBuilder.fromUriString(USERS_LOOKUP_BY_EMAIL_URL)
                .queryParam("email", email)
                .toUriString();

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, request, Map.class
        );

        Map<String, Object> body = response.getBody();
        if (body == null || !Boolean.TRUE.equals(body.get("ok"))) {
            throw new RuntimeException("users.lookupByEmail 실패: error=" + (body != null ? body.get("error") : "null"));
        }

        Map<String, Object> user = (Map<String, Object>) body.get("user");
        if (user == null || user.get("id") == null) {
            throw new RuntimeException("users.lookupByEmail 실패: user 필드 없음");
        }
        return (String) user.get("id");
    }

    public void sendDirectMessage(String slackUserId, String message) {
        String channelId = openDirectMessageChannel(slackUserId);
        postMessage(channelId, message);
    }

    @SuppressWarnings("unchecked")
    private String openDirectMessageChannel(String slackUserId) {
        HttpEntity<Map<String, String>> request = new HttpEntity<>(
                Map.of("users", slackUserId),
                createAuthHeaders()
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                CONVERSATIONS_OPEN_URL, HttpMethod.POST, request, Map.class
        );

        Map<String, Object> body = response.getBody();
        if (body == null || !Boolean.TRUE.equals(body.get("ok"))) {
            throw new RuntimeException("conversations.open 실패: error=" + (body != null ? body.get("error") : "null"));
        }

        Map<String, Object> channel = (Map<String, Object>) body.get("channel");
        if (channel == null || channel.get("id") == null) {
            throw new RuntimeException("conversations.open 실패: channel 필드 없음");
        }
        return (String) channel.get("id");
    }

    private void postMessage(String channelId, String message) {
        HttpEntity<Map<String, String>> request = new HttpEntity<>(
                Map.of("channel", channelId, "text", message),
                createAuthHeaders()
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                CHAT_POST_MESSAGE_URL, HttpMethod.POST, request, Map.class
        );

        Map<String, Object> body = response.getBody();
        if (body == null || !Boolean.TRUE.equals(body.get("ok"))) {
            throw new RuntimeException("chat.postMessage 실패: error=" + (body != null ? body.get("error") : "null"));
        }
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(botToken);
        return headers;
    }
}
