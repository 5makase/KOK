package com.omakase.kok.store.application;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.application.command.ChangeStoreStatusCommand;
import com.omakase.kok.store.application.command.CreateStoreCommand;
import com.omakase.kok.store.application.command.UpdateStoreCommand;
import com.omakase.kok.store.application.result.StoreAmenityResult;
import com.omakase.kok.store.application.result.StoreResult;
import com.omakase.kok.store.application.result.StoreSummaryResult;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreCategory;
import com.omakase.kok.store.domain.entity.StoreHours;
import com.omakase.kok.store.domain.entity.StoreImage;
import com.omakase.kok.store.domain.enums.StoreStatus;
import com.omakase.kok.store.domain.repository.StoreAmenityRepository;
import com.omakase.kok.store.domain.repository.StoreCategoryRepository;
import com.omakase.kok.store.domain.repository.StoreHoursRepository;
import com.omakase.kok.store.domain.repository.StoreImageRepository;
import com.omakase.kok.store.domain.repository.StoreRepository;
import com.omakase.kok.store.domain.repository.StoreSearchCondition;
import com.omakase.kok.store.domain.service.StoreFinder;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private final StoreRepository storeRepository;
    private final StoreCategoryRepository storeCategoryRepository;
    private final StoreHoursRepository storeHoursRepository;
    private final StoreAmenityRepository storeAmenityRepository;
    private final StoreImageRepository storeImageRepository;
    private final StoreFinder storeFinder;

    @Transactional
    public StoreResult createStore(CreateStoreCommand command) {
        StoreCategory category = storeCategoryRepository.findCategory(command.getCategoryId())
                .orElseThrow(() -> new BaseException(StoreErrorCode.CATEGORY_NOT_FOUND));

        // 매장 등록은 2뎁스(소분류) 카테고리만 허용
        if (!category.isSubCategory()) {
            throw new BaseException(StoreErrorCode.INVALID_CATEGORY);
        }

        Store store = Store.create(
                command.getOwnerId(),
                category,
                command.getName(),
                command.getPhone(),
                command.getAddress(),
                command.getDescription(),
                command.getMaxCapacity()
        );

        return StoreResult.from(storeRepository.save(store));
    }

    @Transactional
    public StoreResult updateStore(UpdateStoreCommand command) {
        Store store = storeFinder.findActiveOrThrow(command.getStoreId());
        validateOwner(store, command.getRequesterId());

        StoreCategory category = storeCategoryRepository.findCategory(command.getCategoryId())
                .orElseThrow(() -> new BaseException(StoreErrorCode.CATEGORY_NOT_FOUND));

        if (!category.isSubCategory()) {
            throw new BaseException(StoreErrorCode.INVALID_CATEGORY);
        }

        store.update(
                command.getName(),
                command.getPhone(),
                command.getAddress(),
                command.getDescription(),
                command.getMaxCapacity(),
                category
        );

        return StoreResult.from(store);
    }

    @Transactional
    public void deleteStore(UUID storeId, UUID requesterId) {
        Store store = storeFinder.findActiveOrThrow(storeId);
        // TODO: MASTER 역할이면 소유자 검증 스킵 - Gateway 인가 처리 방식 협의 후 반영
        validateOwner(store, requesterId);
        store.delete(requesterId);
    }

    @Transactional
    public StoreResult changeStatus(ChangeStoreStatusCommand command) {
        Store store = storeFinder.findActiveOrThrow(command.getStoreId());
        // TODO: MASTER 역할이면 소유자 검증 스킵 - Gateway 인가 처리 방식 협의 후 반영
        validateOwner(store, command.getRequesterId());

        // OPEN 전환 시 영업시간 7일치 등록 여부 확인
        if (command.getStatus() == StoreStatus.OPEN
                && storeHoursRepository.countRegisteredHours(store) < 7) {
            throw new BaseException(StoreErrorCode.STORE_HOURS_REQUIRED_FOR_OPEN);
        }

        store.changeStatus(command.getStatus(), command.getRequesterId());
        return StoreResult.from(store);
    }

    // 매장 상세 조회 - 서브 데이터(todayHours/amenities/imagePreview/menuPreview) 포함
    // MASTER: soft delete된 매장도 조회 가능
    public StoreResult getStore(UUID storeId, String role) {
        Store store = "MASTER".equals(role)
                ? storeRepository.findById(storeId)
                        .orElseThrow(() -> new BaseException(StoreErrorCode.STORE_NOT_FOUND))
                : storeFinder.findActiveOrThrow(storeId);

        DayOfWeek today = LocalDate.now().getDayOfWeek();
        StoreHours todayHours = storeHoursRepository.findTodayHours(storeId, today).orElse(null);

        List<StoreAmenityResult> amenities = storeAmenityRepository.findAllAmenities(store)
                .stream().map(StoreAmenityResult::from).toList();

        List<StoreImage> imagePreview = storeImageRepository.findImagePreview(storeId);

        // TODO: Menu 도메인 구현 후 menuPreview 조회 연결
        return StoreResult.of(store, todayHours, amenities, imagePreview, List.of());
    }

    public Page<StoreResult> searchStores(StoreSearchCondition condition, UUID userId, String role, Pageable pageable) {
        return storeRepository.search(resolveCondition(condition, userId, role), pageable).map(StoreResult::from);
    }

    // OWNER: ownerId 자동 주입 / USER·비로그인: OPEN 강제 / MASTER: 조건 그대로
    // TODO: Gateway 인가 필터 구현 후 role/userId 헤더 위조 방어 검토 필요
    private StoreSearchCondition resolveCondition(StoreSearchCondition condition, UUID userId, String role) {
        if ("OWNER".equals(role)) {
            return condition.toBuilder()
                    .ownerId(userId)
                    .build();
        }
        if ("MASTER".equals(role)) {
            return condition;
        }
        return condition.toBuilder()
                .status(StoreStatus.OPEN)
                .build();
    }

    // 내부 API - 웨이팅 서비스에서 매장 기본 정보 조회 시 사용
    public StoreSummaryResult getStoreSummary(UUID storeId) {
        return StoreSummaryResult.from(storeFinder.findActiveOrThrow(storeId));
    }

    private void validateOwner(Store store, UUID requesterId) {
        if (!store.isOwnedBy(requesterId)) {
            throw new BaseException(StoreErrorCode.STORE_ACCESS_DENIED);
        }
    }
}
