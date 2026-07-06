package com.omakase.kok.aiops.prometheus.dto;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PromResult {

    private Map<String, String> metric;
    private List<Object> value;
}
