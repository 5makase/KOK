package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.Menu;
import com.omakase.kok.store.domain.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuJpaRepository extends JpaRepository<Menu, UUID> {

    // menuId + store 조합으로 조회 - 타 매장 메뉴 접근 차단
    Optional<Menu> findByMenuIdAndStoreAndDeletedAtIsNull(UUID menuId, Store store);

    List<Menu> findAllByStoreAndDeletedAtIsNullOrderByDisplayOrderAsc(Store store);

    // 등록 시 displayOrder 중복 체크
    boolean existsByStoreAndDisplayOrderAndDeletedAtIsNull(Store store, int displayOrder);

    // 수정 시 displayOrder 중복 체크 - 자기 자신 제외
    boolean existsByStoreAndDisplayOrderAndMenuIdNotAndDeletedAtIsNull(Store store, int displayOrder, UUID menuId);
}
