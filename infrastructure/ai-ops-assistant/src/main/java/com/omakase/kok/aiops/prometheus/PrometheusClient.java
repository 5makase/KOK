package com.omakase.kok.aiops.prometheus;

import com.omakase.kok.aiops.common.exception.AiOpsErrorCode;
import com.omakase.kok.aiops.common.exception.BaseException;
import com.omakase.kok.aiops.prometheus.dto.PromResponse;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PrometheusClient {

    // LLM이 만든 쿼리가 지나치게 크고 복잡해지는 걸 값싸게 걸러내기 위한 상한 (실제 쿼리들은 200자 내외)
    private static final int MAX_QUERY_LENGTH = 500;

    private final RestClient restClient;
    private final String queryTimeout;

    public PrometheusClient(
            @Value("${prometheus.base-url}") String baseUrl,
            @Value("${prometheus.connect-timeout-ms:1000}") int connectTimeout,
            @Value("${prometheus.read-timeout-ms:5000}") int readTimeout
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
    }

    /**
     * PromQL을 실행하고, LLM이 읽기 좋은 "label => value" 형태로 평탄화
     * 원본 JSON을 그대로 넘기면 토큰 낭비 + 환각 유발이라 여기서 요약
     */
    public String query(String promql) {
        validateQueryLength(promql);

        PromResponse res = restClient.get()
                .uri("/api/v1/query?query={promql}&timeout={timeout}", promql, queryTimeout)
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

    private void validateQueryLength(String promql) {
        if (promql == null || promql.length() > MAX_QUERY_LENGTH) {
            throw new BaseException(AiOpsErrorCode.QUERY_TOO_LONG);
        }
    }
}
