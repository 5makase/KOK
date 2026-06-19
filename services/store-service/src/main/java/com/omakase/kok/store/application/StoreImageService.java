package com.omakase.kok.store.application;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.application.command.AddStoreImageCommand;
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
import java.util.Optional;
import java.util.UUID;

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

        // 요청 내 displayOrder 중복 검증
        boolean hasDuplicate = command.getImages().stream()
                .map(AddStoreImageCommand.ImageEntry::getDisplayOrder)
                .distinct()
                .count() != command.getImages().size();
        if (hasDuplicate) {
            throw new BaseException(StoreErrorCode.STORE_IMAGE_DUPLICATE_DISPLAY_ORDER);
        }

        List<StoreImage> saved = command.getImages().stream()
                .map(entry -> {
                    Optional<StoreImage> existing =
                            storeImageRepository.findImageByDisplayOrder(store, entry.getDisplayOrder());

                    if (existing.isPresent()) {
                        // UniqueConstraint 충돌 방지 - soft delete된 슬롯 재활성화 후 URL 업데이트
                        StoreImage image = existing.get();
                        image.restore();
                        image.update(entry.getImageUrl(), entry.getDisplayOrder());
                        return storeImageRepository.save(image);
                    }

                    return storeImageRepository.save(
                            StoreImage.create(store, entry.getImageUrl(), entry.getDisplayOrder()));
                })
                .toList();

        return saved.stream().map(StoreImageResult::from).toList();
    }

    @Transactional
    public StoreImageResult updateImage(UpdateStoreImageCommand command) {
        StoreImage image = findImage(command.getStoreId(), command.getImageId());
        validateOwner(image.getStore(), command.getRequesterId());

        // 미전달 필드는 기존 값 유지 (부분 수정 허용)
        String newUrl = command.getImageUrl() != null ? command.getImageUrl() : image.getImageUrl();
        int newOrder = command.getDisplayOrder() != null ? command.getDisplayOrder() : image.getDisplayOrder();

        // displayOrder 변경 시 해당 슬롯에 기존 이미지가 있으면 soft delete
        if (command.getDisplayOrder() != null && command.getDisplayOrder() != image.getDisplayOrder()) {
            storeImageRepository.findImageByDisplayOrder(image.getStore(), command.getDisplayOrder())
                    .filter(existing -> !existing.getImageId().equals(image.getImageId()))
                    .ifPresent(existing -> existing.delete(command.getRequesterId()));
        }

        image.update(newUrl, newOrder);
        return StoreImageResult.from(image);
    }

    @Transactional
    public void deleteImage(UUID storeId, UUID imageId, UUID requesterId) {
        StoreImage image = findImage(storeId, imageId);
        validateOwner(image.getStore(), requesterId);
        image.delete(requesterId);
    }

    public List<StoreImageResult> getImages(UUID storeId) {
        Store store = storeFinder.findActiveOrThrow(storeId);
        return storeImageRepository.findAllImages(store).stream()
                .map(StoreImageResult::from)
                .toList();
    }

    private StoreImage findImage(UUID storeId, UUID imageId) {
        return storeImageRepository.findImage(storeId, imageId)
                .orElseThrow(() -> new BaseException(StoreErrorCode.STORE_IMAGE_NOT_FOUND));
    }

    private void validateOwner(Store store, UUID requesterId) {
        if (!store.isOwnedBy(requesterId)) {
            throw new BaseException(StoreErrorCode.STORE_IMAGE_ACCESS_DENIED);
        }
    }
}
