package com.omakase.kok.reservation.presentation.controller;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.reservation.application.dto.CreateSlotRequest;
import com.omakase.kok.reservation.application.dto.SlotResponse;
import com.omakase.kok.reservation.application.service.SlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    @PostMapping
    public ResponseEntity<ApiResponse<SlotResponse>> createSlot(
            @RequestHeader("X-User-Id") UUID ownerId,
            @RequestHeader("X-Role") String role,
            @RequestBody @Valid CreateSlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(slotService.createSlot(request, ownerId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SlotResponse>>> getSlots(
            @RequestHeader("X-User-Id") UUID ownerId,
            @RequestHeader("X-Role") String role,
            @RequestParam UUID storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(slotService.getSlots(storeId, date)));
    }

    @DeleteMapping("/{slotId}")
    public ResponseEntity<ApiResponse<Void>> deleteSlot(
            @RequestHeader("X-User-Id") UUID ownerId,
            @RequestHeader("X-Role") String role,
            @PathVariable("slotId") UUID slotId) {
        slotService.deleteSlot(slotId, ownerId);
        return ResponseEntity.ok(ApiResponse.deleted());
    }
}
