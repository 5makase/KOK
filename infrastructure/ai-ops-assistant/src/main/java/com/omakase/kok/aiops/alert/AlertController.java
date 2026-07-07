package com.omakase.kok.aiops.alert;

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
public class AlertController {

    private final AlertNotificationService alertNotificationService;

    @PostMapping("/api/alerts/analyze")
    public ApiResponse<String> analyze(@RequestBody AlertRequest request) {
        return ApiResponse.success(alertNotificationService.analyzeAndNotify(request.getService()));
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AlertRequest {
        private String service;
    }
}
