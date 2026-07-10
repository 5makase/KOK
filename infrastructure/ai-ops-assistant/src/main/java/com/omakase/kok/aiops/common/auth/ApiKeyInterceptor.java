package com.omakase.kok.aiops.common.auth;

import com.omakase.kok.aiops.common.exception.AiOpsErrorCode;
import com.omakase.kok.aiops.common.exception.BaseException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 운영자만 호출하는 내부 도구: user-service/Gateway의 JWT 인증과는 무관하게 독립적인 API 키로만 접근을 제한한다.
 * Gateway 뒤에 있지 않으므로 X-Role 같은 헤더 기반 권한 체크는 위조 가능해 쓸 수 없다
 */
@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final String apiKey;

    public ApiKeyInterceptor(@Value("${ai-ops.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String provided = request.getHeader(API_KEY_HEADER);
        if (provided == null
                || !MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), apiKey.getBytes(StandardCharsets.UTF_8))) {
            throw new BaseException(AiOpsErrorCode.INVALID_API_KEY);
        }
        return true;
    }
}
