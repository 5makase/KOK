package com.omakase.kok.aiops.agent;

import com.omakase.kok.aiops.prometheus.PrometheusClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tool Calling에서 LLM이 스스로 호출하는 도구 모음
 * LLM이 필요하다고 판단할 때만 실행되며, 몇 번을 호출할지도 LLM이 정한다
 */
@Component
@RequiredArgsConstructor
public class PrometheusTools {

    private final PrometheusClient prometheus;

    @Tool(description = "Prometheus에 PromQL 쿼리를 실행해 현재 메트릭 값을 조회한다")
    public String queryMetric(@ToolParam(description = "실행할 PromQL 쿼리 한 줄") String promql) {
        return prometheus.query(promql);
    }

    @Tool(description = "현재 발생 중인(firing) 알람 목록을 조회한다")
    public String activeAlerts() {
        return prometheus.query("ALERTS{alertstate=\"firing\"}");
    }
}
