package com.omakase.kok.user.presentation.user.controller;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.user.application.user.service.AuthService;
import com.omakase.kok.user.presentation.user.dto.request.LoginRequest;
import com.omakase.kok.user.presentation.user.dto.response.LoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.omakase.kok.common.dto.ApiResponse;

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
}
