package com.omakase.kok.reservation.presentation.controller;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.reservation.application.dto.CreateSlotRequest;
import com.omakase.kok.reservation.application.dto.SlotResponse;
import com.omakase.kok.reservation.application.dto.UpdateSlotRequest;
import com.omakase.kok.reservation.application.service.SlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "슬롯 관리")
@RestController
@RequestMapping("/api/v1/slots")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class SlotController {

    private final SlotService slotService;

    @Operation(summary = "슬롯 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<SlotResponse>> createSlot(
            @RequestHeader("X-User-Id") UUID ownerId,
            @RequestBody @Valid CreateSlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(slotService.createSlot(request, ownerId)));
    }

    @Operation(summary = "슬롯 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SlotResponse>>> getSlots(
            @RequestParam UUID storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(slotService.getSlots(storeId, date)));
    }

    @Operation(summary = "슬롯 단건 조회")
    @GetMapping("/{slotId}")
    public ResponseEntity<ApiResponse<SlotResponse>> getSlot(
            @PathVariable("slotId") UUID slotId) {
        return ResponseEntity.ok(ApiResponse.success(slotService.getSlot(slotId)));
    }

    @Operation(summary = "슬롯 수정")
    @PatchMapping("/{slotId}")
    public ResponseEntity<ApiResponse<SlotResponse>> updateSlot(
            @RequestHeader("X-User-Id") UUID ownerId,
            @PathVariable("slotId") UUID slotId,
            @RequestBody @Valid UpdateSlotRequest request) {
        return ResponseEntity.ok(ApiResponse.success(slotService.updateSlot(slotId, request, ownerId)));
    }

    @Operation(summary = "슬롯 삭제")
    @DeleteMapping("/{slotId}")
    public ResponseEntity<ApiResponse<Void>> deleteSlot(
            @RequestHeader("X-User-Id") UUID ownerId,
            @PathVariable("slotId") UUID slotId) {
        slotService.deleteSlot(slotId, ownerId);
        return ResponseEntity.ok(ApiResponse.deleted());
    }
}
