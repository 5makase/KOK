package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoreJpaRepository extends JpaRepository<Store, UUID> {

    Optional<Store> findByStoreIdAndDeletedAtIsNull(UUID storeId);
}
