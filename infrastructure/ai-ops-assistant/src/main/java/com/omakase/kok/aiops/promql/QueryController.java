package com.omakase.kok.aiops.promql;

import com.omakase.kok.aiops.common.dto.ApiResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class QueryController {

    private final NlToPromQL nlToPromQL;

    @PostMapping("/api/query")
    public ApiResponse<String> query(@RequestBody QueryRequest request) {
        return ApiResponse.success(nlToPromQL.answer(request.getQuestion()));
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class QueryRequest {
        private String question;
    }
}
