package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.Menu;
import com.omakase.kok.store.domain.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuJpaRepository extends JpaRepository<Menu, UUID> {

    Optional<Menu> findByMenuIdAndDeletedAtIsNull(UUID menuId);

    List<Menu> findAllByStoreAndDeletedAtIsNullOrderByDisplayOrderAsc(Store store);
}
