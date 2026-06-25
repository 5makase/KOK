package com.omakase.kok.user.presentation.user.dto.response;

import com.omakase.kok.user.domain.user.entity.User;
import com.omakase.kok.user.domain.user.enums.Role;

import java.util.UUID;

public record UserDetailResponse(
        UUID userId,
        String username,
        String name,
        String email,
        String phone,
        String slackId,
        Role role
) {
    public static UserDetailResponse from(User user) {
        return new UserDetailResponse(
                user.getUserId(),
                user.getUsername(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getSlackId(),
                user.getRole()
        );
    }
}
