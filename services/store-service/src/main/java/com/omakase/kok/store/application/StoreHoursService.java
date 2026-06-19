package com.omakase.kok.store.application;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.application.command.CreateStoreHoursBulkCommand;
import com.omakase.kok.store.application.command.UpdateStoreHoursCommand;
import com.omakase.kok.store.application.result.StoreHoursResult;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreHours;
import com.omakase.kok.store.domain.repository.StoreHoursRepository;
import com.omakase.kok.store.domain.service.StoreFinder;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreHoursService {

    private final StoreHoursRepository storeHoursRepository;
    private final StoreFinder storeFinder;

    @Transactional
    public List<StoreHoursResult> createBulkHours(CreateStoreHoursBulkCommand command) {
        Store store = storeFinder.findActiveOrThrow(command.getStoreId());
        validateOwner(store, command.getRequesterId());

        List<StoreHours> saved = command.getHours().stream()
                .map(entry -> {
                    // restore/신규 경로 무관하게 선검증
                    validateHoursEntry(entry.isDayOff(), entry.getOpenTime());

                    Optional<StoreHours> existing =
                            storeHoursRepository.findHoursByDay(store, entry.getDayOfWeek());

                    if (existing.isPresent()) {
                        // UniqueConstraint 충돌 방지 — soft delete된 로우 재활성화 후 업데이트
                        StoreHours hours = existing.get();
                        hours.restore();
                        hours.update(entry.getOpenTime(), entry.getCloseTime(),
                                entry.getBreakStartTime(), entry.getBreakEndTime(), entry.isDayOff());
                        return storeHoursRepository.save(hours);
                    }

                    return storeHoursRepository.save(StoreHours.create(
                            store,
                            entry.getDayOfWeek(),
                            entry.getOpenTime(),
                            entry.getCloseTime(),
                            entry.getBreakStartTime(),
                            entry.getBreakEndTime(),
                            entry.isDayOff()
                    ));
                })
                .toList();

        return saved.stream().map(StoreHoursResult::from).toList();
    }

    @Transactional
    public StoreHoursResult updateHours(UpdateStoreHoursCommand command) {
        StoreHours hours = findHours(command.getHoursId());
        validateOwner(hours.getStore(), command.getRequesterId());
        validateHoursEntry(command.isDayOff(), command.getOpenTime());

        hours.update(command.getOpenTime(), command.getCloseTime(),
                command.getBreakStartTime(), command.getBreakEndTime(), command.isDayOff());

        return StoreHoursResult.from(hours);
    }

    @Transactional
    public void deleteHours(UUID hoursId, UUID requesterId) {
        StoreHours hours = findHours(hoursId);
        validateOwner(hours.getStore(), requesterId);

        // OPEN 매장의 영업시간 삭제 차단 — 7개 미만 시 OPEN 상태가 깨지므로
        if (hours.getStore().isAvailableForService()) {
            throw new BaseException(StoreErrorCode.STORE_HOURS_CANNOT_DELETE_WHILE_OPEN);
        }

        hours.delete(requesterId);
    }

    public List<StoreHoursResult> getStoreHours(UUID storeId) {
        Store store = storeFinder.findActiveOrThrow(storeId);
        return storeHoursRepository.findAllHours(store).stream()
                .map(StoreHoursResult::from)
                .toList();
    }

    private StoreHours findHours(UUID hoursId) {
        return storeHoursRepository.findHours(hoursId)
                .orElseThrow(() -> new BaseException(StoreErrorCode.STORE_HOURS_NOT_FOUND));
    }

    private void validateOwner(Store store, UUID requesterId) {
        if (!store.isOwnedBy(requesterId)) {
            throw new BaseException(StoreErrorCode.STORE_HOURS_ACCESS_DENIED);
        }
    }

    // 영업일(isDayOff=false)인데 openTime이 없으면 유효하지 않은 입력
    private void validateHoursEntry(boolean isDayOff, LocalTime openTime) {
        if (!isDayOff && openTime == null) {
            throw new BaseException(StoreErrorCode.INVALID_STORE_HOURS);
        }
    }
}
