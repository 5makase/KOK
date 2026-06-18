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
    public Optional<StoreImage> findImage(UUID imageId) {
        // 활성 이미지 단건 조회 (deletedAt IS NULL)
        return storeImageJpaRepository.findByImageIdAndDeletedAtIsNull(imageId);
    }

    @Override
    public List<StoreImage> findAllImages(Store store) {
        // 해당 매장의 활성 이미지 목록 조회 — displayOrder 오름차순 정렬
        return storeImageJpaRepository.findAllByStoreAndDeletedAtIsNullOrderByDisplayOrderAsc(store);
    }
}
