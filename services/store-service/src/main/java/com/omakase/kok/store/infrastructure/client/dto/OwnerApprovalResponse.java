package com.omakase.kok.store.infrastructure.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

// user-service GET api 응답 DTO
@Getter
@NoArgsConstructor
public class OwnerApprovalResponse {
    private UUID userId;
    private Boolean approved;
}
