package com.omakase.kok.aiops.promql;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PromQLPlan {

    private String promql;
    private String reason;
}
