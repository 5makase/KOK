package com.kok.review.infrastructure.client;

import com.kok.review.infrastructure.client.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-service")
public interface UserClient {
    @GetMapping("/api/v1/users/{userId}")
    UserResponse getUser(@PathVariable("userId") UUID userId);
}
