package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreJpaRepository extends JpaRepository<Store, UUID> {

    // soft delete된 매장 제외 단건 조회
    Optional<Store> findByStoreIdAndDeletedAtIsNull(UUID storeId);

    // 해당 카테고리에 활성 매장 존재 여부 - 카테고리 삭제 전 사전 검증용
    boolean existsByCategoryAndDeletedAtIsNull(StoreCategory category);

    // 랭킹 조회 전용 - JOIN FETCH로 category를 한 번에 로딩해 N+1 방지
    // StoreRankingResult.of()가 category.getName()을 접근하므로 LAZY 프록시 추가 조회가 발생하지 않도록 함
    @Query("SELECT s FROM Store s JOIN FETCH s.category WHERE s.storeId IN :ids AND s.deletedAt IS NULL")
    List<Store> findActiveStoresByIdsWithCategory(@Param("ids") List<UUID> ids);
}
