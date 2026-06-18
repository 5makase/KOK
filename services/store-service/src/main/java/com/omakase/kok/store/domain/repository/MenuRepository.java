package com.omakase.kok.store.domain.repository;

import com.omakase.kok.store.domain.entity.Menu;
import com.omakase.kok.store.domain.entity.Store;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuRepository {

    Menu save(Menu menu);

    // 활성 메뉴 단건 조회
    Optional<Menu> findMenu(UUID menuId);

    // 매장의 활성 메뉴 목록 조회 (정렬순서 오름차순)
    List<Menu> findAllMenus(Store store);
}
