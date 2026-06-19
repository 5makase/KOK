package com.omakase.kok.store.application;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.application.command.AddStoreAmenityCommand;
import com.omakase.kok.store.application.result.StoreAmenityResult;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreAmenity;
import com.omakase.kok.store.domain.repository.StoreAmenityRepository;
import com.omakase.kok.store.domain.service.StoreFinder;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreAmenityService {

    private final StoreAmenityRepository storeAmenityRepository;
    private final StoreFinder storeFinder;

    @Transactional
    public StoreAmenityResult addAmenity(AddStoreAmenityCommand command) {
        Store store = storeFinder.findActiveOrThrow(command.getStoreId());
        validateOwner(store, command.getRequesterId());

        Optional<StoreAmenity> existing =
                storeAmenityRepository.findAmenityByType(store, command.getAmenityType());

        StoreAmenity amenity;
        if (existing.isPresent()) {
            StoreAmenity found = existing.get();
            if (!found.isDeleted()) {
                // active 상태의 동일 타입 재등록 시도 - 중복 등록 차단
                throw new BaseException(StoreErrorCode.AMENITY_ALREADY_EXISTS);
            }
            // soft delete된 편의시설 재활성화 (UniqueConstraint 충돌 방지)
            found.restore();
            amenity = found;
        } else {
            amenity = StoreAmenity.create(store, command.getAmenityType());
        }

        return StoreAmenityResult.from(storeAmenityRepository.save(amenity));
    }

    @Transactional
    public void deleteAmenity(UUID storeId, UUID amenityId, UUID requesterId) {
        StoreAmenity amenity = findAmenity(storeId, amenityId);
        validateOwner(amenity.getStore(), requesterId);
        amenity.delete(requesterId);
    }

    public List<StoreAmenityResult> getAmenities(UUID storeId) {
        Store store = storeFinder.findActiveOrThrow(storeId);
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
