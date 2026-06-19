package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.Store;
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
    public Optional<StoreImage> findImage(UUID storeId, UUID imageId) {
        return storeImageJpaRepository.findByStoreStoreIdAndImageIdAndDeletedAtIsNull(storeId, imageId);
    }

    @Override
    public Optional<StoreImage> findImageByDisplayOrder(Store store, int displayOrder) {
        return storeImageJpaRepository.findByStoreAndDisplayOrder(store, displayOrder);
    }

    @Override
    public List<StoreImage> findAllImages(Store store) {
        return storeImageJpaRepository.findAllByStoreAndDeletedAtIsNullOrderByDisplayOrderAsc(store);
    }
}
