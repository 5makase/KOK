package com.omakase.kok.user.presentation.user.dto.response;

import com.omakase.kok.user.domain.user.enums.Role;

import java.util.UUID;

public record SignupResponse(
        UUID userId,
        String username,
        String email,
        Role role,
        String message
) {
}
