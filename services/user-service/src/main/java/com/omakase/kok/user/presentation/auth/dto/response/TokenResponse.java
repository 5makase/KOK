package com.omakase.kok.user.presentation.auth.dto.response;

public class TokenResponse {
    public record Refresh(
            String accessToken,
            String refreshToken
    ) {
        public static Refresh of(String accessToken, String refreshToken) {
            return new Refresh(accessToken, refreshToken);
        }
    }
}
