package com.omakase.kok.user.presentation.user.controller;

import com.omakase.kok.user.application.user.service.UserService;
import com.omakase.kok.user.global.dto.ApiResponse;
import com.omakase.kok.user.presentation.user.dto.request.SignupRequest;
import com.omakase.kok.user.presentation.user.dto.response.SignupResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/users/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signupUser(
            @Valid @RequestBody SignupRequest request
    ) {
        SignupResponse response = userService.signupUser(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("USER 회원가입이 완료되었습니다.", response));
    }

    @PostMapping("/owners/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signupOwner(
            @Valid @RequestBody SignupRequest request
    ) {
        SignupResponse response = userService.signupOwner(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("OWNER 회원가입 요청이 완료되었습니다.", response));
    }
}