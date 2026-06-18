package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreImageJpaRepository extends JpaRepository<StoreImage, UUID> {

    Optional<StoreImage> findByImageIdAndDeletedAtIsNull(UUID imageId);

    List<StoreImage> findAllByStoreAndDeletedAtIsNullOrderByDisplayOrderAsc(Store store);
}
