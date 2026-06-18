package com.omakase.kok.store.domain.repository;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreHours;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreHoursRepository {

    StoreHours save(StoreHours storeHours);

    void saveAll(List<StoreHours> storeHoursList);

    // 활성 영업시간 단건 조회
    Optional<StoreHours> findHours(UUID hoursId);

    // 매장의 활성 영업시간 목록 조회
    List<StoreHours> findAllHours(Store store);

    // 요일로 영업시간 조회 (삭제 포함 — restore 처리용)
    Optional<StoreHours> findHoursByDay(Store store, DayOfWeek dayOfWeek);

    // 매장의 등록된 영업시간 수 (OPEN 전환 가능 여부 확인용)
    long countRegisteredHours(Store store);
}
