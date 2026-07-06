package com.omakase.kok.aiops.prometheus;

/**
 * LLM 시스템 프롬프트에 반복 삽입되는 메트릭 레이블 규칙. 여러 프롬프트가 참조하므로 한 곳에서만 관리한다
 */
public final class MetricConventions {

    public static final String JOB_LABEL_RULE =
            "서비스 구분은 job 레이블로 한다.(예: job=\"waiting-service\")";

    private MetricConventions() {
    }
}
