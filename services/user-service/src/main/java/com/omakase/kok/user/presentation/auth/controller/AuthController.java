package com.omakase.kok.user.presentation.auth.controller;

import com.omakase.kok.common.response.ApiResponse;
import com.omakase.kok.user.application.auth.service.AuthService;
import com.omakase.kok.user.presentation.auth.dto.request.TokenRefreshRequest;
import com.omakase.kok.user.presentation.auth.dto.response.TokenResponse;
import com.omakase.kok.user.presentation.user.dto.request.LoginRequest;
import com.omakase.kok.user.presentation.user.dto.response.LoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse.Refresh>> refresh(
            @Valid @RequestBody TokenRefreshRequest request
    ) {
        TokenResponse.Refresh response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}