package com.omakase.kok.notification.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
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
