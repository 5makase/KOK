package com.omakase.kok.store.presentation;

import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.common.dto.PageResponse;
import com.omakase.kok.store.application.StoreService;
import com.omakase.kok.store.application.command.ChangeStoreStatusCommand;
import com.omakase.kok.store.application.command.CreateStoreCommand;
import com.omakase.kok.store.application.command.UpdateStoreCommand;
import com.omakase.kok.store.application.result.StoreResult;
import com.omakase.kok.store.domain.enums.AmenityType;
import com.omakase.kok.store.domain.enums.StoreStatus;
import com.omakase.kok.store.domain.repository.StoreSearchCondition;
import com.omakase.kok.store.domain.vo.Address;
import com.omakase.kok.store.presentation.dto.request.ChangeStoreStatusRequest;
import com.omakase.kok.store.presentation.dto.request.CreateStoreRequest;
import com.omakase.kok.store.presentation.dto.request.UpdateStoreRequest;
import com.omakase.kok.store.presentation.dto.response.StoreResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    // 매장 등록
    @PostMapping
    public ResponseEntity<ApiResponse<StoreResponse>> createStore(
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @Valid @RequestBody CreateStoreRequest request
    ) {
        Address address = new Address(
                request.getAddressSido(), request.getAddressSigungu(),
                request.getAddressDong(), request.getAddressDetail(),
                request.getLatitude(), request.getLongitude()
        );
        CreateStoreCommand command = CreateStoreCommand.builder()
                .ownerId(userId)
                .categoryId(request.getCategoryId())
                .name(request.getName())
                .phone(request.getPhone())
                .address(address)
                .description(request.getDescription())
                .maxCapacity(request.getMaxCapacity())
                .build();
        return ResponseEntity.status(201).body(ApiResponse.created(StoreResponse.from(storeService.createStore(command))));
    }

    // 매장 기본정보 수정
    @PatchMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreResponse>> updateStore(
            @PathVariable UUID storeId,
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @Valid @RequestBody UpdateStoreRequest request
    ) {
        // addressSido가 없으면 주소 변경 없음 - null 전달 시 Store.update()에서 기존 값 유지
        Address address = request.getAddressSido() != null
                ? new Address(request.getAddressSido(), request.getAddressSigungu(),
                        request.getAddressDong(), request.getAddressDetail(),
                        request.getLatitude(), request.getLongitude())
                : null;
        UpdateStoreCommand command = UpdateStoreCommand.builder()
                .storeId(storeId)
                .requesterId(userId)
                .categoryId(request.getCategoryId())
                .name(request.getName())
                .phone(request.getPhone())
                .address(address)
                .description(request.getDescription())
                .maxCapacity(request.getMaxCapacity())
                .build();
        return ResponseEntity.ok(ApiResponse.success(StoreResponse.from(storeService.updateStore(command))));
    }

    // 매장 상태 변경
    @PatchMapping("/{storeId}/status")
    public ResponseEntity<ApiResponse<StoreResponse>> changeStatus(
            @PathVariable UUID storeId,
            @RequestHeader(AuthConstants.USER_ID) UUID userId,
            @RequestHeader(value = AuthConstants.ROLE, required = false) String role,
            @Valid @RequestBody ChangeStoreStatusRequest request
    ) {
        ChangeStoreStatusCommand command = ChangeStoreStatusCommand.builder()
                .storeId(storeId)
                .requesterId(userId)
                .status(request.getStatus())
                .role(role)
                .build();
        return ResponseEntity.ok(ApiResponse.success(StoreResponse.from(storeService.changeStatus(command))));
    }

    // 매장 상세 조회 - MASTER는 soft delete된 매장도 조회 가능
    @GetMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreResponse>> getStore(
            @PathVariable UUID storeId,
            @RequestHeader(value = AuthConstants.ROLE, required = false) String role
    ) {
        return ResponseEntity.ok(ApiResponse.success(StoreResponse.from(storeService.getStore(storeId, role))));
    }

    // 매장 목록 검색 - 역할별 동작 차이는 Service에서 처리
    // USER: status=OPEN 강제 / OWNER: 본인 매장 전체 상태 자동 적용 / MASTER: 모든 조건 자유
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<StoreResponse>>> searchStores(
            @RequestHeader(value = AuthConstants.USER_ID, required = false) UUID userId,
            @RequestHeader(value = AuthConstants.ROLE, required = false) String role,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String sido,
            @RequestParam(required = false) String sigungu,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<AmenityType> amenities,
            @RequestParam(required = false) StoreStatus status,
            @RequestParam(required = false) StoreSearchCondition.SortType sort,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) Double radiusKm,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        StoreSearchCondition condition = StoreSearchCondition.builder()
                .categoryId(categoryId)
                .sido(sido)
                .sigungu(sigungu)
                .keyword(keyword)
                .amenities(amenities)
                .status(status)
                .sort(sort)
                .latitude(latitude)
                .longitude(longitude)
                .radiusKm(radiusKm)
                .build();

        Page<StoreResult> page = storeService.searchStores(condition, userId, role, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(page.map(StoreResponse::from))));
    }
}
