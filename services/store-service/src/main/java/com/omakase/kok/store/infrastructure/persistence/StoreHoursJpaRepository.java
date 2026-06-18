package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreHoursJpaRepository extends JpaRepository<StoreHours, UUID> {

    Optional<StoreHours> findByHoursIdAndDeletedAtIsNull(UUID hoursId);

    List<StoreHours> findAllByStoreAndDeletedAtIsNull(Store store);

    Optional<StoreHours> findByStoreAndDayOfWeek(Store store, DayOfWeek dayOfWeek);

    long countByStoreAndDeletedAtIsNull(Store store);
}
