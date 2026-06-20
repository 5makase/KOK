package com.omakase.kok.store.application;

import com.omakase.kok.common.exception.BaseException;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreImageService {

    private final StoreImageRepository storeImageRepository;
    private final StoreFinder storeFinder;

    @Transactional
    public List<StoreImageResult> addImages(AddStoreImageCommand command) {
        Store store = storeFinder.findActiveOrThrow(command.getStoreId());
        validateOwner(store, command.getRequesterId());
        validateNoDuplicateOrders(command.getImages());

        // 요청 슬롯 한 번에 조회 (soft delete 포함)
        List<Integer> orders = extractOrders(command.getImages());
        Map<Integer, StoreImage> existingByOrder = fetchExistingByOrder(store.getStoreId(), orders);

        // 슬롯 상태에 따라 restore or create
        List<StoreImage> toSave = command.getImages().stream()
                .map(entry -> upsert(existingByOrder, store, entry))
                .toList();

        return storeImageRepository.saveAll(toSave).stream()
                .map(StoreImageResult::from)
                .toList();
    }

    @Transactional
    public StoreImageResult updateImage(UpdateStoreImageCommand command) {
        StoreImage image = findImage(command.getStoreId(), command.getImageId());
        validateOwner(image.getStore(), command.getRequesterId());

        // displayOrder 변경 시 목표 슬롯에 활성 이미지가 있으면 soft delete (슬롯 교체)
        if (command.getDisplayOrder() != image.getDisplayOrder()) {
            storeImageRepository.findImageByDisplayOrder(command.getStoreId(), command.getDisplayOrder())
                    .filter(existing -> !existing.getImageId().equals(image.getImageId()))
                    .filter(existing -> !existing.isDeleted())
                    .ifPresent(existing -> existing.delete(command.getRequesterId()));
        }

        image.update(command.getImageUrl(), command.getDisplayOrder());
        return StoreImageResult.from(image);
    }

    @Transactional
    public void deleteImage(UUID storeId, UUID imageId, UUID requesterId) {
        StoreImage image = findImage(storeId, imageId);
        validateOwner(image.getStore(), requesterId);
        image.delete(requesterId);
    }

    public List<StoreImageResult> getImages(UUID storeId) {
        storeFinder.findActiveOrThrow(storeId); // 매장 존재 및 활성 상태 검증
        return storeImageRepository.findAllImages(storeId).stream()
                .map(StoreImageResult::from)
                .toList();
    }

    private void validateNoDuplicateOrders(List<ImageEntry> images) {
        long distinctCount = images.stream()
                .map(ImageEntry::getDisplayOrder)
                .distinct()
                .count();
        if (distinctCount != images.size()) {
            throw new BaseException(StoreErrorCode.STORE_IMAGE_DUPLICATE_DISPLAY_ORDER);
        }
    }

    private List<Integer> extractOrders(List<ImageEntry> images) {
        return images.stream().map(ImageEntry::getDisplayOrder).toList();
    }

    private Map<Integer, StoreImage> fetchExistingByOrder(UUID storeId, List<Integer> orders) {
        return storeImageRepository.findAllByDisplayOrders(storeId, orders).stream()
                .collect(Collectors.toMap(StoreImage::getDisplayOrder, i -> i));
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
        StoreImage image = storeImageRepository.findImage(storeId, imageId)
                .orElseThrow(() -> new BaseException(StoreErrorCode.STORE_IMAGE_NOT_FOUND));
        // soft delete 포함 조회 후 명시적 체크 - 이미 삭제된 경우
        if (image.isDeleted()) {
            throw new BaseException(StoreErrorCode.STORE_IMAGE_ALREADY_DELETED);
        }
        return image;
    }

    private void validateOwner(Store store, UUID requesterId) {
        if (!store.isOwnedBy(requesterId)) {
            throw new BaseException(StoreErrorCode.STORE_IMAGE_ACCESS_DENIED);
        }
    }
}
