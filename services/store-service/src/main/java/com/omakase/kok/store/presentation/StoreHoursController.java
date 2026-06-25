package com.omakase.kok.store.presentation;

import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.auth.RoleAuthorizationUtils;
import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.store.application.StoreHoursService;
import com.omakase.kok.store.application.command.CreateStoreHoursBulkCommand;
import com.omakase.kok.store.application.command.UpdateStoreHoursCommand;
import com.omakase.kok.store.presentation.dto.request.CreateStoreHoursBulkRequest;
import com.omakase.kok.store.presentation.dto.request.UpdateStoreHoursRequest;
import com.omakase.kok.store.presentation.dto.response.StoreHoursResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/hours")
@RequiredArgsConstructor
public class StoreHoursController {

    private final StoreHoursService storeHoursService;

    // 영업시간 7일치 일괄 등록 (이미 등록된 요일은 restore 후 덮어쓰기)
    @PostMapping
    public ResponseEntity<ApiResponse<List<StoreHoursResponse>>> createBulkHours(
            @PathVariable UUID storeId,
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(AuthConstants.ROLE) String role,
            @Valid @RequestBody CreateStoreHoursBulkRequest request
    ) {
        RoleAuthorizationUtils.requireAnyRole(role, AuthConstants.OWNER, AuthConstants.MASTER);
        CreateStoreHoursBulkCommand command = CreateStoreHoursBulkCommand.builder()
                .storeId(storeId)
                .requesterId(userId)
                .hours(request.getHours().stream()
                        .map(h -> CreateStoreHoursBulkCommand.HoursEntry.builder()
                                .dayOfWeek(h.getDayOfWeek())
                                .openTime(h.getOpenTime())
                                .closeTime(h.getCloseTime())
                                .breakStartTime(h.getBreakStartTime())
                                .breakEndTime(h.getBreakEndTime())
                                .isDayOff(h.getIsDayOff())
                                .build())
                        .toList())
                .build();
        List<StoreHoursResponse> response = storeHoursService.createBulkHours(command, role).stream()
                .map(StoreHoursResponse::from).toList();
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    // 영업시간 수정
    @PatchMapping("/{hoursId}")
    public ResponseEntity<ApiResponse<StoreHoursResponse>> updateHours(
            @PathVariable UUID storeId,
            @PathVariable UUID hoursId,
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(AuthConstants.ROLE) String role,
            @Valid @RequestBody UpdateStoreHoursRequest request
    ) {
        RoleAuthorizationUtils.requireAnyRole(role, AuthConstants.OWNER, AuthConstants.MASTER);
        UpdateStoreHoursCommand command = UpdateStoreHoursCommand.builder()
                .storeId(storeId)
                .hoursId(hoursId)
                .requesterId(userId)
                .openTime(request.getOpenTime())
                .closeTime(request.getCloseTime())
                .breakStartTime(request.getBreakStartTime())
                .breakEndTime(request.getBreakEndTime())
                .isDayOff(request.getIsDayOff())
                .build();
        return ResponseEntity.ok(ApiResponse.success(StoreHoursResponse.from(storeHoursService.updateHours(command, role))));
    }

    // 영업시간 삭제 (OPEN 매장 불가 - isDayOff 변경 유도)
    @DeleteMapping("/{hoursId}")
    public ResponseEntity<ApiResponse<Void>> deleteHours(
            @PathVariable UUID storeId,
            @PathVariable UUID hoursId,
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(AuthConstants.ROLE) String role
    ) {
        RoleAuthorizationUtils.requireAnyRole(role, AuthConstants.OWNER, AuthConstants.MASTER);
        storeHoursService.deleteHours(storeId, hoursId, userId, role);
        return ResponseEntity.ok(ApiResponse.deleted());
    }

    // 매장 영업시간 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreHoursResponse>>> getStoreHours(
            @PathVariable UUID storeId
    ) {
        List<StoreHoursResponse> response = storeHoursService.getStoreHours(storeId).stream()
                .map(StoreHoursResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
