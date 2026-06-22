package com.omakase.kok.store.application;

import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.auth.RoleAuthorizationUtils;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.application.command.AddStoreAmenityCommand;
import com.omakase.kok.store.application.result.StoreAmenityResult;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreAmenity;
import com.omakase.kok.store.domain.enums.AmenityType;
import com.omakase.kok.store.domain.repository.StoreAmenityRepository;
import com.omakase.kok.store.domain.service.StoreFinder;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreAmenityService {

    private final StoreAmenityRepository storeAmenityRepository;
    private final StoreFinder storeFinder;

    @Transactional
    public StoreAmenityResult.Bulk syncAmenities(AddStoreAmenityCommand command) {
        Store store = storeFinder.findActiveOrThrow(command.getStoreId());
        validateOwner(store, command.getRequesterId());

        Set<AmenityType> requested = Set.copyOf(command.getAmenityTypes());

        // 1회 쿼리로 전체 편의시설 조회 (soft delete 포함) → 타입 기준 Map
        // (store_id, amenity_type) UniqueConstraint + restore() 패턴으로 타입당 DB 행이 항상 1개 → 중복 키 없음
        Map<AmenityType, StoreAmenity> existingByType = storeAmenityRepository
                .findAllAmenitiesIncludingDeleted(store).stream()
                .collect(Collectors.toMap(StoreAmenity::getAmenityType, a -> a));

        List<StoreAmenity> toSave = new ArrayList<>();

        // 요청 타입 처리 - restore 또는 신규 생성
        for (AmenityType type : requested) {
            StoreAmenity existing = existingByType.get(type);
            if (existing != null) {
                if (existing.isDeleted()) {
                    existing.restore();
                }
                toSave.add(existing);
            } else {
                toSave.add(StoreAmenity.create(store, type));
            }
        }

        // 요청에 없는 기존 active 편의시설 soft delete
        existingByType.values().stream()
                .filter(a -> !a.isDeleted() && !requested.contains(a.getAmenityType()))
                .forEach(a -> {
                    a.delete(command.getRequesterId());
                    toSave.add(a);
                });

        storeAmenityRepository.saveAll(toSave);

        List<StoreAmenityResult> activeAmenities = toSave.stream()
                .filter(a -> !a.isDeleted())
                .map(StoreAmenityResult::from)
                .toList();

        return StoreAmenityResult.Bulk.builder()
                .storeId(store.getStoreId())
                .amenities(activeAmenities)
                .build();
    }

    @Transactional
    public void deleteAmenity(UUID storeId, UUID amenityId, UUID requesterId, String role) {
        Store store = storeFinder.findActiveOrThrow(storeId);
        if (!RoleAuthorizationUtils.hasAnyRole(role, AuthConstants.MASTER)) {
            validateOwner(store, requesterId);
        }
        StoreAmenity amenity = findAmenity(storeId, amenityId);
        // soft delete 포함 조회 후 명시적 체크 - 이미 삭제된 경우
        if (amenity.isDeleted()) {
            throw new BaseException(StoreErrorCode.AMENITY_ALREADY_DELETED);
        }
        amenity.delete(requesterId);
    }

    public List<StoreAmenityResult> getAmenities(UUID storeId) {
        Store store = storeFinder.findActiveOrThrow(storeId);
        // 편의시설이 없으면 빈 리스트 반환
        return storeAmenityRepository.findAllAmenities(store).stream()
                .map(StoreAmenityResult::from)
                .toList();
    }

    private StoreAmenity findAmenity(UUID storeId, UUID amenityId) {
        return storeAmenityRepository.findAmenity(storeId, amenityId)
                .orElseThrow(() -> new BaseException(StoreErrorCode.AMENITY_NOT_FOUND));
    }

    private void validateOwner(Store store, UUID requesterId) {
        if (!store.isOwnedBy(requesterId)) {
            throw new BaseException(StoreErrorCode.AMENITY_ACCESS_DENIED);
        }
    }
}
