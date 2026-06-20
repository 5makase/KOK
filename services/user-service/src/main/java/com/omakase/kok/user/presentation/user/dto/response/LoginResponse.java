package com.omakase.kok.user.presentation.user.dto.response;

import com.omakase.kok.user.domain.user.entity.User;
import lombok.Builder;

import java.util.UUID;

@Builder
public record LoginResponse(
        UUID userId,
        String username,
        String role,
        String accessToken,
        String refreshToken
) {

    public static LoginResponse of(
            User user,
            String accessToken,
            String refreshToken
    ) {
        return LoginResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .role(user.getRole().name())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}