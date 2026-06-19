package com.omakase.kok.store.application;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.application.command.ChangeStoreStatusCommand;
import com.omakase.kok.store.application.command.CreateStoreCommand;
import com.omakase.kok.store.application.command.UpdateStoreCommand;
import com.omakase.kok.store.application.result.StoreResult;
import com.omakase.kok.store.application.result.StoreSummaryResult;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreCategory;
import com.omakase.kok.store.domain.enums.StoreStatus;
import com.omakase.kok.store.domain.repository.StoreCategoryRepository;
import com.omakase.kok.store.domain.repository.StoreHoursRepository;
import com.omakase.kok.store.domain.repository.StoreRepository;
import com.omakase.kok.store.domain.repository.StoreSearchCondition;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private final StoreRepository storeRepository;
    private final StoreCategoryRepository storeCategoryRepository;
    private final StoreHoursRepository storeHoursRepository;

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
                command.getAddressSido(),
                command.getAddressSigungu(),
                command.getAddressDong(),
                command.getAddressDetail(),
                command.getLatitude(),
                command.getLongitude(),
                command.getDescription(),
                command.getMaxCapacity()
        );

        return StoreResult.from(storeRepository.save(store));
    }

    @Transactional
    public StoreResult updateStore(UpdateStoreCommand command) {
        Store store = findActiveStore(command.getStoreId());
        validateOwner(store, command.getRequesterId());

        StoreCategory category = storeCategoryRepository.findCategory(command.getCategoryId())
                .orElseThrow(() -> new BaseException(StoreErrorCode.CATEGORY_NOT_FOUND));

        if (!category.isSubCategory()) {
            throw new BaseException(StoreErrorCode.INVALID_CATEGORY);
        }

        store.update(
                command.getName(),
                command.getPhone(),
                command.getAddressSido(),
                command.getAddressSigungu(),
                command.getAddressDong(),
                command.getAddressDetail(),
                command.getDescription(),
                command.getMaxCapacity(),
                category
        );

        return StoreResult.from(store);
    }

    @Transactional
    public void deleteStore(UUID storeId, UUID requesterId) {
        Store store = findActiveStore(storeId);
        // TODO: MASTER 역할이면 소유자 검증 스킵 - Gateway 인가 처리 방식 협의 후 반영
        validateOwner(store, requesterId);
        store.delete(requesterId);
    }

    @Transactional
    public StoreResult changeStatus(ChangeStoreStatusCommand command) {
        Store store = findActiveStore(command.getStoreId());
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

    public StoreResult getStore(UUID storeId) {
        return StoreResult.from(findActiveStore(storeId));
    }

    public Page<StoreResult> searchStores(StoreSearchCondition condition, UUID userId, String role, Pageable pageable) {
        return storeRepository.search(resolveCondition(condition, userId, role), pageable).map(StoreResult::from);
    }

    // 역할별 검색 조건 결정
    // OWNER: 헤더의 userId를 ownerId로 자동 주입, status 미지정 시 전체 상태 조회
    // MASTER: 요청 조건 그대로 적용
    // USER/비로그인: status 무시하고 OPEN 강제
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

    // 내부 API — 웨이팅 서비스에서 매장 기본 정보 조회 시 사용
    public StoreSummaryResult getStoreSummary(UUID storeId) {
        return StoreSummaryResult.from(findActiveStore(storeId));
    }

    private Store findActiveStore(UUID storeId) {
        return storeRepository.findStore(storeId)
                .orElseThrow(() -> new BaseException(StoreErrorCode.STORE_NOT_FOUND));
    }

    private void validateOwner(Store store, UUID requesterId) {
        if (!store.isOwnedBy(requesterId)) {
            throw new BaseException(StoreErrorCode.STORE_ACCESS_DENIED);
        }
    }
}
