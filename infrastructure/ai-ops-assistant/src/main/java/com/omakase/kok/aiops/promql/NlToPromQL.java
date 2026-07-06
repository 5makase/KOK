package com.omakase.kok.aiops.promql;

import com.omakase.kok.aiops.prometheus.MetricConventions;
import com.omakase.kok.aiops.prometheus.PrometheusClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 자연어 질문 -> PromQL로 변환해 조회 -> 결과를 자연어로 요약해 응답
 * LLM은 쿼리 문자열 생성과 결과 해석만 담당하고, 실행은 항상 PrometheusClient가 한다
 */
@Service
@RequiredArgsConstructor
public class NlToPromQL {

    private final ChatClient chatClient;
    private final PrometheusClient prometheus;

    public String answer(String question) {
        PromQLPlan plan = chatClient.prompt()
                .system("""
                        너는 PromQL 전문가다. 사용자의 질문을 PromQL 한 줄로 변환하라.
                        우리 메트릭 규칙:
                        - %s
                        - HTTP: http_server_requests_seconds_count{job, status, uri}
                        - 지연시간 히스토그램: http_server_requests_seconds_bucket
                        - p99는 histogram_quantile(0.99, sum(rate(...bucket[5m])) by (le))
                        실행은 하지 말고 쿼리와 이유만 채워라.
                        """.formatted(MetricConventions.JOB_LABEL_RULE))
                .user(question)
                .call()
                .entity(PromQLPlan.class);

        String metric = prometheus.query(plan.getPromql());

        return chatClient.prompt()
                .system("아래 메트릭 결과를 운영자에게 한국어로 간결히 설명하라. 근거 숫자를 포함하라.")
                .user("질문: %s\n실행 쿼리: %s\n결과:\n%s".formatted(question, plan.getPromql(), metric))
                .call()
                .content();
    }
}
