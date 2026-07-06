package com.omakase.kok.aiops.prometheus.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PromData {

    private String resultType;
    private List<PromResult> result;
}
