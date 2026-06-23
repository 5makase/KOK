package com.omakase.kok.user.presentation.auth.controller;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.user.application.auth.AuthService;
import com.omakase.kok.user.presentation.auth.dto.request.TokenRefreshRequest;
import com.omakase.kok.user.presentation.auth.dto.response.TokenResponse;
import com.omakase.kok.user.presentation.user.dto.request.LoginRequest;
import com.omakase.kok.user.presentation.user.dto.response.LoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse.Refresh>> refresh(
            @Valid @RequestBody TokenRefreshRequest request
    ) {
        TokenResponse.Refresh response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
