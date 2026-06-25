package com.omakase.kok.notification.infrastructure.client;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.notification.infrastructure.client.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

// TODO: user-service에 서비스 간 내부 호출 전용 엔드포인트(/api/v1/internal/users/{userId}) 추가 후 교체
//       현재는 기존 MASTER 전용 엔드포인트를 임시 사용 (X-Role: MASTER 하드코딩)
@FeignClient(name = "user-service")
public interface UserServiceClient {

/*
    @GetMapping("/api/v1/internal/users/{userId}")
    ApiResponse<UserResponse> getUser(@PathVariable("userId") UUID userId);
 */

    @GetMapping("/api/v1/users/{userId}")
    ApiResponse<UserResponse> getUser(
            @PathVariable("userId") UUID userId,
            @RequestHeader("X-Role") String role
    );
}
