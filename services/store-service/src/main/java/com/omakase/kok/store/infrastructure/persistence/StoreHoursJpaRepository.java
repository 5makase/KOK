package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreHoursJpaRepository extends JpaRepository<StoreHours, UUID> {

    // storeId + hoursId 복합 조건 - 다중 매장 점주의 교차 접근 차단
    Optional<StoreHours> findByStoreStoreIdAndHoursIdAndDeletedAtIsNull(UUID storeId, UUID hoursId);

    List<StoreHours> findAllByStoreAndDeletedAtIsNullOrderByDayOfWeekAsc(Store store);

    Optional<StoreHours> findByStoreAndDayOfWeek(Store store, DayOfWeek dayOfWeek);

    long countByStoreAndDeletedAtIsNull(Store store);
}
