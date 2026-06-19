package com.omakase.kok.reservation.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RefundRequest {

    private Long refundAmount;
}
