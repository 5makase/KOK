package com.omakase.kok.store.domain.repository;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreImage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreImageRepository {

    StoreImage save(StoreImage image);

    // 활성 이미지 단건 조회
    Optional<StoreImage> findImage(UUID imageId);

    // 매장의 활성 이미지 목록 조회 (정렬순서 오름차순)
    List<StoreImage> findAllImages(Store store);
}
