package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.StoreImage;
import com.omakase.kok.store.domain.repository.StoreImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class StoreImageRepositoryImpl implements StoreImageRepository {

    private final StoreImageJpaRepository storeImageJpaRepository;

    @Override
    public StoreImage save(StoreImage image) {
        return storeImageJpaRepository.save(image);
    }

    @Override
    public List<StoreImage> saveAll(List<StoreImage> images) {
        return storeImageJpaRepository.saveAll(images);
    }

    @Override
    public Optional<StoreImage> findImage(UUID storeId, UUID imageId) {
        return storeImageJpaRepository.findByStoreStoreIdAndImageIdAndDeletedAtIsNull(storeId, imageId);
    }

    @Override
    public Optional<StoreImage> findImageByDisplayOrder(UUID storeId, int displayOrder) {
        return storeImageJpaRepository.findByStoreStoreIdAndDisplayOrder(storeId, displayOrder);
    }

    @Override
    public List<StoreImage> findAllByDisplayOrders(UUID storeId, List<Integer> displayOrders) {
        return storeImageJpaRepository.findAllByStoreStoreIdAndDisplayOrderIn(storeId, displayOrders);
    }

    @Override
    public List<StoreImage> findAllImages(UUID storeId) {
        return storeImageJpaRepository.findAllByStoreStoreIdAndDeletedAtIsNullOrderByDisplayOrderAsc(storeId);
    }

    @Override
    public List<StoreImage> findImagePreview(UUID storeId) {
        return storeImageJpaRepository.findTop5ByStoreStoreIdAndDeletedAtIsNullOrderByDisplayOrderAsc(storeId);
    }
}
