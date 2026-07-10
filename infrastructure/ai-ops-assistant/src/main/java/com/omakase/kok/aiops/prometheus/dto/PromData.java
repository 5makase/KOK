package com.omakase.kok.aiops.prometheus.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PromData {

    private String resultType;
    private Object result;
}
