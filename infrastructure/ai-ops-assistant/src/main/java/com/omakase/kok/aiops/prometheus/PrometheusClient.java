package com.omakase.kok.aiops.prometheus;

import com.omakase.kok.aiops.prometheus.dto.PromResponse;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PrometheusClient {

    private final RestClient restClient;

    public PrometheusClient(@Value("${prometheus.base-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    /**
     * PromQL을 실행하고, LLM이 읽기 좋은 "label => value" 형태로 평탄화
     * 원본 JSON을 그대로 넘기면 토큰 낭비 + 환각 유발이라 여기서 요약
     */
    public String query(String promql) {
        PromResponse res = restClient.get()
                .uri("/api/v1/query?query={promql}", promql)
                .retrieve()
                .body(PromResponse.class);

        if (res == null || !"success".equals(res.getStatus()) || res.getData() == null) {
            return "쿼리 실패: " + promql;
        }

        if (res.getData().getResult().isEmpty()) {
            return "결과 없음: " + promql;
        }

        return res.getData().getResult().stream()
                .map(r -> r.getMetric() + " => " + r.getValue().get(1))
                .collect(Collectors.joining("\n"));
    }
}
