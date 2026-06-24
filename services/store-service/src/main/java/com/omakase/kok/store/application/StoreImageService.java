package com.omakase.kok.store.application;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.application.validator.StoreOwnerValidator;
import com.omakase.kok.store.application.command.AddStoreImageCommand;
import com.omakase.kok.store.application.command.AddStoreImageCommand.ImageEntry;
import com.omakase.kok.store.application.command.UpdateStoreImageCommand;
import com.omakase.kok.store.application.result.StoreImageResult;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreImage;
import com.omakase.kok.store.domain.repository.StoreImageRepository;
import com.omakase.kok.store.domain.service.StoreFinder;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreImageService {

    private final StoreImageRepository storeImageRepository;
    private final StoreFinder storeFinder;
    private final StoreOwnerValidator storeOwnerValidator;

    @Transactional
    public List<StoreImageResult> addImages(AddStoreImageCommand command, String role) {
        Store store = storeFinder.findActiveOrThrow(command.getStoreId());
        storeOwnerValidator.validate(store, command.getRequesterId(), role, StoreErrorCode.STORE_IMAGE_ACCESS_DENIED);
        validateNoDuplicateOrders(command.getImages());

        // 요청 슬롯 한 번에 조회 (soft delete 포함)
        List<Integer> displayOrders = command.getImages().stream().map(ImageEntry::getDisplayOrder).toList();
        Map<Integer, StoreImage> existingByOrder = fetchExistingByOrder(store.getStoreId(), displayOrders);

        // 슬롯 상태에 따라 restore or create
        List<StoreImage> toSave = command.getImages().stream()
                .map(entry -> upsert(existingByOrder, store, entry))
                .toList();

        return storeImageRepository.saveAll(toSave).stream()
                .map(StoreImageResult::from)
                .toList();
    }

    @Transactional
    public StoreImageResult updateImage(UpdateStoreImageCommand command, String role) {
        // 매장 활성 상태 검증 - soft delete된 매장의 이미지가 수정되는 것을 방지
        Store store = storeFinder.findActiveOrThrow(command.getStoreId());
        StoreImage image = findImage(command.getStoreId(), command.getImageId());
        storeOwnerValidator.validate(store, command.getRequesterId(), role, StoreErrorCode.STORE_IMAGE_ACCESS_DENIED);

        // displayOrder 변경 시 목표 슬롯에 활성 이미지가 있으면 슬롯을 즉시 비운 뒤 현재 이미지를 이동
        if (!Objects.equals(command.getDisplayOrder(), image.getDisplayOrder())) {
            storeImageRepository.findImageByDisplayOrder(command.getStoreId(), command.getDisplayOrder())
                    .filter(existing -> !existing.getImageId().equals(image.getImageId()))
                    .filter(existing -> !existing.isDeleted())
                    .ifPresent(existing -> {
                        existing.delete(command.getRequesterId());
                        storeImageRepository.releaseImageSlot(existing);
                    });
        }

        image.update(command.getImageUrl(), command.getDisplayOrder());
        return StoreImageResult.from(image);
    }

    @Transactional
    public void deleteImage(UUID storeId, UUID imageId, UUID requesterId, String role) {
        // 매장 활성 상태 검증 - soft delete된 매장의 이미지가 삭제되는 것을 방지
        Store store = storeFinder.findActiveOrThrow(storeId);
        StoreImage image = findImage(storeId, imageId);
        storeOwnerValidator.validate(store, requesterId, role, StoreErrorCode.STORE_IMAGE_ACCESS_DENIED);
        image.delete(requesterId);
    }

    public List<StoreImageResult> getImages(UUID storeId) {
        storeFinder.findActiveOrThrow(storeId); // 매장 존재 및 활성 상태 검증
        return storeImageRepository.findAllImages(storeId).stream()
                .map(StoreImageResult::from)
                .toList();
    }

    private void validateNoDuplicateOrders(List<ImageEntry> images) {
        Set<Integer> seen = new HashSet<>();
        for (ImageEntry entry : images) {
            if (!seen.add(entry.getDisplayOrder())) {
                throw new BaseException(StoreErrorCode.STORE_IMAGE_DUPLICATE_DISPLAY_ORDER);
            }
        }
    }

    private Map<Integer, StoreImage> fetchExistingByOrder(UUID storeId, List<Integer> orders) {
        // 같은 슬롯에 soft delete된 행과 활성 행이 공존할 수 있음 (updateImage 후 addImages 호출 시)
        // 중복 키 충돌 방지를 위해 활성 행(deleted_at IS NULL) 우선으로 merge
        return storeImageRepository.findAllByDisplayOrders(storeId, orders).stream()
                .collect(Collectors.toMap(
                        StoreImage::getDisplayOrder,
                        i -> i,
                        (existing, incoming) -> existing.isDeleted() ? incoming : existing
                ));
    }

    // 슬롯 상태에 따라 restore(재활성화) or create(신규 생성)
    private StoreImage upsert(Map<Integer, StoreImage> existingByOrder, Store store, ImageEntry entry) {
        StoreImage existing = existingByOrder.get(entry.getDisplayOrder());
        if (existing != null) {
            if (existing.isDeleted()) {
                existing.restore();
            }
            existing.update(entry.getImageUrl(), entry.getDisplayOrder());
            return existing;
        }
        return StoreImage.create(store, entry.getImageUrl(), entry.getDisplayOrder());
    }

    private StoreImage findImage(UUID storeId, UUID imageId) {
        StoreImage image = storeImageRepository.findImageById(storeId, imageId)
                .orElseThrow(() -> new BaseException(StoreErrorCode.STORE_IMAGE_NOT_FOUND));
        // soft delete 포함 조회 후 명시적 체크 - 이미 삭제된 경우
        if (image.isDeleted()) {
            throw new BaseException(StoreErrorCode.STORE_IMAGE_ALREADY_DELETED);
        }
        return image;
    }

}
