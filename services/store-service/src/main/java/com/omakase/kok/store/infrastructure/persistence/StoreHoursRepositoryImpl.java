package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreHours;
import com.omakase.kok.store.domain.repository.StoreHoursRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class StoreHoursRepositoryImpl implements StoreHoursRepository {

    private final StoreHoursJpaRepository storeHoursJpaRepository;

    @Override
    public StoreHours save(StoreHours storeHours) {
        return storeHoursJpaRepository.save(storeHours);
    }

    @Override
    public void saveAll(List<StoreHours> storeHoursList) {
        // 영업시간 일괄 등록 (7일치 한 번에 저장)
        storeHoursJpaRepository.saveAll(storeHoursList);
    }

    @Override
    public Optional<StoreHours> findHours(UUID hoursId) {
        // 활성 영업시간 단건 조회 (deletedAt IS NULL)
        return storeHoursJpaRepository.findByHoursIdAndDeletedAtIsNull(hoursId);
    }

    @Override
    public List<StoreHours> findAllHours(Store store) {
        // 해당 매장의 활성 영업시간 전체 조회
        return storeHoursJpaRepository.findAllByStoreAndDeletedAtIsNull(store);
    }

    @Override
    public Optional<StoreHours> findHoursByDay(Store store, DayOfWeek dayOfWeek) {
        // 특정 요일 영업시간 조회 — soft delete된 로우도 포함 (restore 패턴용)
        return storeHoursJpaRepository.findByStoreAndDayOfWeek(store, dayOfWeek);
    }

    @Override
    public long countRegisteredHours(Store store) {
        // 활성 영업시간 등록 수 — OPEN 전환 가능 여부 판단에 사용 (7개 기준)
        return storeHoursJpaRepository.countByStoreAndDeletedAtIsNull(store);
    }
}
