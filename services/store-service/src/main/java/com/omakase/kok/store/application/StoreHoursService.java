package com.omakase.kok.store.application;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.application.validator.StoreOwnerValidator;
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
    private final StoreOwnerValidator storeOwnerValidator;

    @Transactional
    public List<StoreHoursResult> createBulkHours(CreateStoreHoursBulkCommand command, String role) {
        Store store = storeFinder.findActiveOrThrow(command.getStoreId());
        storeOwnerValidator.validate(store, command.getRequesterId(), role, StoreErrorCode.STORE_HOURS_ACCESS_DENIED);

        List<StoreHours> saved = command.getHours().stream()
                .map(entry -> {
                    // 영업일(isDayOff=false)인데 openTime/closeTime 없으면 유효하지 않은 입력
                    validateHoursEntry(entry.isDayOff(), entry.getOpenTime(), entry.getCloseTime());

                    Optional<StoreHours> existing =
                            storeHoursRepository.findHoursByDay(store, entry.getDayOfWeek());

                    if (existing.isPresent()) {
                        // UniqueConstraint 충돌 방지 - soft delete된 로우 재활성화 후 업데이트
                        StoreHours hours = existing.get();
                        hours.restore();
                        if (entry.isDayOff()) {
                            hours.updateToDayOff();
                        } else {
                            hours.updateToOperating(entry.getOpenTime(), entry.getCloseTime(),
                                    entry.getBreakStartTime(), entry.getBreakEndTime());
                        }
                        return storeHoursRepository.save(hours);
                    }

                    StoreHours newHours = entry.isDayOff()
                            ? StoreHours.createDayOff(store, entry.getDayOfWeek())
                            : StoreHours.createOperating(store, entry.getDayOfWeek(),
                                    entry.getOpenTime(), entry.getCloseTime(),
                                    entry.getBreakStartTime(), entry.getBreakEndTime());
                    return storeHoursRepository.save(newHours);
                })
                .toList();

        return saved.stream().map(StoreHoursResult::from).toList();
    }

    @Transactional
    public StoreHoursResult updateHours(UpdateStoreHoursCommand command, String role) {
        // 매장 활성 상태 검증 - soft delete된 매장의 영업시간이 수정되는 것을 방지
        Store store = storeFinder.findActiveOrThrow(command.getStoreId());
        StoreHours hours = findHours(command.getStoreId(), command.getHoursId());
        storeOwnerValidator.validate(store, command.getRequesterId(), role, StoreErrorCode.STORE_HOURS_ACCESS_DENIED);
        validateHoursEntry(command.isDayOff(), command.getOpenTime(), command.getCloseTime());

        if (command.isDayOff()) {
            hours.updateToDayOff();
        } else {
            hours.updateToOperating(command.getOpenTime(), command.getCloseTime(),
                    command.getBreakStartTime(), command.getBreakEndTime());
        }

        return StoreHoursResult.from(hours);
    }

    @Transactional
    public void deleteHours(UUID storeId, UUID hoursId, UUID requesterId, String role) {
        // 매장 활성 상태 검증 - soft delete된 매장의 영업시간이 삭제되는 것을 방지
        Store store = storeFinder.findActiveOrThrow(storeId);
        StoreHours hours = findHours(storeId, hoursId);
        storeOwnerValidator.validate(store, requesterId, role, StoreErrorCode.STORE_HOURS_ACCESS_DENIED);

        if (hours.isDeleted()) {
            throw new BaseException(StoreErrorCode.STORE_HOURS_ALREADY_DELETED);
        }

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

    private StoreHours findHours(UUID storeId, UUID hoursId) {
        return storeHoursRepository.findHours(storeId, hoursId)
                .orElseThrow(() -> new BaseException(StoreErrorCode.STORE_HOURS_NOT_FOUND));
    }

    // 영업일(isDayOff=false)인데 openTime 또는 closeTime이 없으면 유효하지 않은 입력
    private void validateHoursEntry(boolean isDayOff, LocalTime openTime, LocalTime closeTime) {
        if (!isDayOff && (openTime == null || closeTime == null)) {
            throw new BaseException(StoreErrorCode.INVALID_STORE_HOURS);
        }
    }
}
