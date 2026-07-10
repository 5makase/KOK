package com.omakase.kok.aiops.prometheus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.aiops.common.exception.AiOpsErrorCode;
import com.omakase.kok.aiops.common.exception.BaseException;
import com.omakase.kok.aiops.prometheus.dto.PromData;
import com.omakase.kok.aiops.prometheus.dto.PromResponse;
import com.omakase.kok.aiops.prometheus.dto.PromResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class PrometheusClient {

    // LLM이 만든 쿼리가 지나치게 크고 복잡해지는 걸 값싸게 걸러내기 위한 상한 (실제 쿼리들은 200자 내외)
    private static final int MAX_QUERY_LENGTH = 500;

    private final RestClient restClient;
    private final String queryTimeout;
    private final ObjectMapper objectMapper;

    public PrometheusClient(
            @Value("${prometheus.base-url}") String baseUrl,
            @Value("${prometheus.connect-timeout-ms:1000}") int connectTimeout,
            @Value("${prometheus.read-timeout-ms:5000}") int readTimeout,
            ObjectMapper objectMapper
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();

        // Prometheus API의 timeout 파라미터로 서버 쪽에서도 직접 쿼리를 중단시키게 한다.
        this.queryTimeout = Math.max(1, readTimeout / 1000) + "s";
        this.objectMapper = objectMapper;
    }

    /**
     * PromQL을 실행하고, LLM이 읽기 좋은 "label => value" 형태로 평탄화
     * 원본 JSON을 그대로 넘기면 토큰 낭비 + 환각 유발이라 여기서 요약
     */
    public String query(String promql) {
        validateQuery(promql);

        PromResponse res = restClient.get()
                .uri("/api/v1/query?query={promql}&timeout={timeout}", promql, queryTimeout)
                .retrieve()
                .body(PromResponse.class);

        if (res == null || !"success".equals(res.getStatus()) || res.getData() == null) {
            return "쿼리 실패: " + promql;
        }

        return formatResult(res.getData(), promql);
    }

    private String formatResult(PromData data, String promql) {
        return switch (data.getResultType()) {
            case "vector" -> formatVector(data.getResult(), promql);
            case "scalar", "string" -> formatScalarOrString(data.getResult(), promql);
            default -> "지원하지 않는 결과 타입(" + data.getResultType() + "): " + promql;
        };
    }

    private String formatVector(Object rawResult, String promql) {
        List<PromResult> results = objectMapper.convertValue(rawResult, new TypeReference<>() {
        });

        if (results.isEmpty()) return "결과 없음: " + promql;

        return results.stream()
                .map(r -> r.getMetric() + " => " + r.getValue().get(1))
                .collect(Collectors.joining("\n"));
    }

    private String formatScalarOrString(Object rawResult, String promql) {
        List<?> pair = objectMapper.convertValue(rawResult, List.class);
        if (pair == null || pair.size() < 2) return "결과 없음: " + promql;

        return String.valueOf(pair.get(1));
    }

    private void validateQuery(String promql) {
        if (promql == null || promql.isBlank()) throw new BaseException(AiOpsErrorCode.INVALID_PROMQL);

        if (promql.length() > MAX_QUERY_LENGTH) throw new BaseException(AiOpsErrorCode.QUERY_TOO_LONG);
    }
}
