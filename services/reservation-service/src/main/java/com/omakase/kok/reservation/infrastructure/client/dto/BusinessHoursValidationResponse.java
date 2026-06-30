package com.omakase.kok.reservation.infrastructure.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BusinessHoursValidationResponse {

    private boolean available;
    private String reason;
}
