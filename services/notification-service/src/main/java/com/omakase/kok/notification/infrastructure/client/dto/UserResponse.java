package com.omakase.kok.notification.infrastructure.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@Setter
public class UserResponse {

    private UUID userId;
    private String email;
    private String name;
    private String phone;
    private String slackId;
    private String role;
    private String approvalStatus;
    private UUID approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
