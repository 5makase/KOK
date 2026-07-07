package com.omakase.kok.aiops.prometheus.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PromResponse {

    private String status;
    private PromData data;
}
