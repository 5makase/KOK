package com.omakase.kok.waiting.presentation.controller;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.waiting.application.service.WaitingService;
import com.omakase.kok.waiting.presentation.dto.request.WaitingCreateRequest;
import com.omakase.kok.waiting.presentation.dto.response.WaitingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/waitings")
public class WaitingController {
    private static final String USER_ID_HEADER = "X-User-Id";

    private final WaitingService waitingService;

    // 웨이팅 등록
    @PostMapping
    public ResponseEntity<ApiResponse<WaitingResponse>> createWaiting(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @Valid @RequestBody WaitingCreateRequest request
    ) {
        WaitingResponse response = waitingService.createWaiting(userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }
}
