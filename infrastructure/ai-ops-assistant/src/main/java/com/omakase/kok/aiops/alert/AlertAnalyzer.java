package com.omakase.kok.aiops.alert;

import com.omakase.kok.aiops.prometheus.PrometheusClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 알람 발생 시 미리 정한 진단 메트릭을 수집 -> LLM에게 원인 후보 요약을 요청
 * (p99 지연시간, 5xx 에러율, CPU 사용률, DB 커넥션 풀 사용률)
 */
@Service
@RequiredArgsConstructor
public class AlertAnalyzer {

    private final ChatClient chatClient;
    private final PrometheusClient prometheus;

    public String analyze(String service) {
        String context = """
                [모니터링 대상 여부(up)] %s
                [지연시간 p99] %s
                [5xx 에러율] %s
                [CPU 사용률] %s
                [DB 커넥션 풀 사용률] %s
                """.formatted(
                prometheus.query("up{job=\"%s\"}".formatted(service)),
                prometheus.query("histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{job=\"%s\"}[5m])) by (le))".formatted(service)),
                prometheus.query("sum(rate(http_server_requests_seconds_count{job=\"%s\",status=~\"5..\"}[5m]))".formatted(service)),
                prometheus.query("avg_over_time(process_cpu_usage{job=\"%s\"}[5m])".formatted(service)),
                prometheus.query("hikaricp_connections_active{job=\"%s\"} / hikaricp_connections_max{job=\"%s\"}".formatted(service, service))
        );

        return chatClient.prompt()
                .system("""
                        너는 장애 분석 보조다. 아래 메트릭을 종합해 가장 가능성 높은 원인 후보를 2~3개, 확신도(상/중/하)와 함께 제시하라. 
                        근거 없는 단정은 금지. 다음 확인할 액션도 제안하라.
                        [모니터링 대상 여부(up)]를 가장 먼저 확인하라: 결과 없음이면 이 job이 Prometheus에 아예 등록되지 않은 것이고, 
                        up=0이면 등록은 됐으나 응답이 없는 것이다. 
                        두 경우 모두 나머지 메트릭의 "결과 없음"을 "정상"으로 해석하지 말고, 모니터링 공백 또는 서비스 다운 가능성부터 보고하라.
                        """)
                .user("서비스: %s\n%s".formatted(service, context))
                .call()
                .content();
    }
}
