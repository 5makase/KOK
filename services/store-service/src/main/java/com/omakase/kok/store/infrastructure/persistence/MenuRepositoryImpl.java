package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.Menu;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MenuRepositoryImpl implements MenuRepository {

    private final MenuJpaRepository menuJpaRepository;

    @Override
    public Menu save(Menu menu) {
        return menuJpaRepository.save(menu);
    }

    @Override
    public Optional<Menu> findMenu(UUID menuId) {
        // 활성 메뉴 단건 조회 (deletedAt IS NULL)
        return menuJpaRepository.findByMenuIdAndDeletedAtIsNull(menuId);
    }

    @Override
    public List<Menu> findAllMenus(Store store) {
        // 해당 매장의 활성 메뉴 목록 조회 — displayOrder 오름차순 정렬
        return menuJpaRepository.findAllByStoreAndDeletedAtIsNullOrderByDisplayOrderAsc(store);
    }
}
