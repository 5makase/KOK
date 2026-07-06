package com.omakase.kok.store.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.common.exception.CommonErrorCode;
import com.omakase.kok.store.application.cache.StoreListCacheRepository;
import com.omakase.kok.store.application.command.ChangeStoreStatusCommand;
import com.omakase.kok.store.application.command.CreateStoreCommand;
import com.omakase.kok.store.application.command.UpdateStoreCommand;
import com.omakase.kok.store.application.port.OwnerApprovalPort;
import com.omakase.kok.store.application.result.StoreAmenityResult;
import com.omakase.kok.store.application.result.StoreResult;
import com.omakase.kok.store.application.result.StoreSummaryResult;
import com.omakase.kok.store.application.validator.StoreOwnerValidator;
import com.omakase.kok.store.domain.entity.Menu;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreCategory;
import com.omakase.kok.store.domain.entity.StoreHours;
import com.omakase.kok.store.domain.entity.StoreImage;
import com.omakase.kok.store.domain.entity.StoreOutboxEvent;
import com.omakase.kok.store.domain.enums.StoreEventType;
import com.omakase.kok.store.domain.enums.StoreStatus;
import com.omakase.kok.store.domain.repository.MenuRepository;
import com.omakase.kok.store.domain.repository.StoreAmenityRepository;
import com.omakase.kok.store.domain.repository.StoreCategoryRepository;
import com.omakase.kok.store.domain.repository.StoreHoursRepository;
import com.omakase.kok.store.domain.repository.StoreImageRepository;
import com.omakase.kok.store.domain.repository.StoreOutboxEventRepository;
import com.omakase.kok.store.domain.repository.StoreRepository;
import com.omakase.kok.store.domain.repository.StoreSearchCondition;
import com.omakase.kok.store.domain.service.StoreFinder;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import com.omakase.kok.store.global.util.TransactionUtils;
import com.omakase.kok.store.infrastructure.kafka.event.StoreCreatedEvent;
import com.omakase.kok.store.infrastructure.kafka.event.StoreEventEnvelope;
import com.omakase.kok.store.infrastructure.kafka.event.StoreEventFactory;
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

    // 도메인 Repository
    private final StoreRepository storeRepository;
    private final StoreCategoryRepository storeCategoryRepository;
    private final StoreHoursRepository storeHoursRepository;
    private final StoreAmenityRepository storeAmenityRepository;
    private final StoreImageRepository storeImageRepository;
    private final MenuRepository menuRepository;
    private final StoreOutboxEventRepository storeOutboxEventRepository;

    // 조회/캐시
    private final StoreFinder storeFinder;
    private final StoreListCacheRepository storeListCacheRepository;

    // 검증
    private final StoreOwnerValidator storeOwnerValidator;
    private final OwnerApprovalPort ownerApprovalPort;

    // Kafka Outbox 이벤트 생성
    private final StoreEventFactory storeEventFactory;

    private final ObjectMapper objectMapper;

    @Transactional
    public StoreResult createStore(CreateStoreCommand command) {
        // 승인된 OWNER만 매장 등록 가능: user-service 동기 검증
        if (!ownerApprovalPort.isApproved(command.getOwnerId())) {
            throw new BaseException(StoreErrorCode.OWNER_NOT_APPROVED); // 미승인 시 403
        }

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

        // 영속화
        Store savedStore = storeRepository.save(store);

        // Outbox 이벤트 저장 (매장 저장과 같은 트랜잭션->원자성 보장): waiting-service가 STORE_CREATED 구독 → 기본 웨이팅 설정 자동 초기화
        saveStoreCreatedOutboxEvent(savedStore);

        StoreResult result = StoreResult.from(savedStore);
        evictListCacheAfterCommit();
        return result;
    }

    @Transactional
    public StoreResult updateStore(UpdateStoreCommand command, String role) {
        Store store = storeFinder.findActiveOrThrow(command.getStoreId());
        storeOwnerValidator.validate(store, command.getRequesterId(), role, StoreErrorCode.STORE_ACCESS_DENIED);

        StoreCategory category;
        if (command.getCategoryId() != null) {
            category = storeCategoryRepository.findCategory(command.getCategoryId())
                    .orElseThrow(() -> new BaseException(StoreErrorCode.CATEGORY_NOT_FOUND));
            if (!category.isSubCategory()) {
                throw new BaseException(StoreErrorCode.INVALID_CATEGORY);
            }
        } else {
            category = store.getCategory();
        }

        store.update(
                command.getName(),
                command.getPhone(),
                command.getAddress(),
                command.getDescription(),
                command.getMaxCapacity(),
                category
        );

        evictListCacheAfterCommit();
        return StoreResult.from(store);
    }

    @Transactional
    public void deleteStore(UUID storeId, UUID requesterId, String role) {
        Store store = storeFinder.findActiveOrThrow(storeId);
        storeOwnerValidator.validate(store, requesterId, role, StoreErrorCode.STORE_ACCESS_DENIED);
        store.delete(requesterId);
        evictListCacheAfterCommit();
    }

    @Transactional
    public StoreResult changeStatus(ChangeStoreStatusCommand command) {
        Store store = storeFinder.findActiveOrThrow(command.getStoreId());
        storeOwnerValidator.validate(store, command.getRequesterId(), command.getRole(), StoreErrorCode.STORE_ACCESS_DENIED);

        // OPEN 전환일 때만 영업시간 등록 개수를 조회. 그 외 전환에는 이 값이 쓰이지 않으므로 조회 생략
        // 7일치 이상 등록됐는지 여부는 Store.changeStatus() 내부에서 검증
        int registeredHoursCount = command.getStatus() == StoreStatus.OPEN
                ? (int) storeHoursRepository.countRegisteredHours(store)
                : 0;

        store.changeStatus(command.getStatus(), registeredHoursCount, command.getRequesterId());

        evictListCacheAfterCommit();
        return StoreResult.from(store);
    }

    // 매장 상세 조회 - 서브 데이터(todayHours/amenities/imagePreview/menuPreview) 포함
    // MASTER: soft delete된 매장도 조회 가능

    public StoreResult getStore(UUID storeId, String role) {
        Store store = AuthConstants.MASTER.equals(role)
                ? storeRepository.findById(storeId)
                        .orElseThrow(() -> new BaseException(StoreErrorCode.STORE_NOT_FOUND))
                : storeFinder.findActiveOrThrow(storeId);

        DayOfWeek today = LocalDate.now().getDayOfWeek();
        StoreHours todayHours = storeHoursRepository.findTodayHours(storeId, today).orElse(null);

        List<StoreAmenityResult> amenities = storeAmenityRepository.findAllAmenities(store)
                .stream().map(StoreAmenityResult::from).toList();

        List<StoreImage> imagePreview = storeImageRepository.findImagePreview(storeId);
        List<Menu> menuPreview = menuRepository.findAllMenus(store);

        return StoreResult.of(store, todayHours, amenities, imagePreview, menuPreview);
    }
    public Page<StoreResult> searchStores(StoreSearchCondition condition, UUID categoryId, UUID userId, String role, Pageable pageable) {
        StoreSearchCondition withCategory = resolveCategoryIds(condition, categoryId);
        StoreSearchCondition resolved = resolveCondition(withCategory, userId, role);

        // OWNER는 본인 매장만 조회 - 캐시 효과 낮고 다른 OWNER 캐시와 격리 필요
        if (AuthConstants.OWNER.equals(role)) {
            return storeRepository.search(resolved, pageable).map(StoreResult::from);
        }

        return storeListCacheRepository.get(resolved, pageable).orElseGet(() -> {
            Page<StoreResult> result = storeRepository.search(resolved, pageable).map(StoreResult::from);
            storeListCacheRepository.set(resolved, pageable, result);
            return result;
        });
    }

    // 대분류 ID면 활성 소분류 ID 목록으로 확장, 소분류 ID면 단일 목록, null이면 조건 없음
    // 카테고리 계층 해석은 도메인 규칙이므로 인프라(Repository)가 아닌 이 레이어에서 처리한다

    private StoreSearchCondition resolveCategoryIds(StoreSearchCondition condition, UUID categoryId) {
        if (categoryId == null) return condition;
        StoreCategory category = storeCategoryRepository.findCategory(categoryId)
                .orElseThrow(() -> new BaseException(StoreErrorCode.CATEGORY_NOT_FOUND));
        List<UUID> categoryIds = category.isSubCategory()
                ? List.of(categoryId)
                : category.getChildren().stream()
                        .filter(child -> !child.isDeleted())
                        .map(StoreCategory::getCategoryId)
                        .toList();
        return condition.toBuilder().categoryIds(categoryIds).build();
    }
    // OWNER: ownerId 자동 주입 / USER·비로그인: OPEN 강제 / MASTER: 조건 그대로

    private StoreSearchCondition resolveCondition(StoreSearchCondition condition, UUID userId, String role) {
        if (AuthConstants.OWNER.equals(role)) {
            // userId null이면 ownerId 필터가 무효화되어 전체 매장이 조회되므로 반드시 차단
            if (userId == null) {
                throw new BaseException(CommonErrorCode.ACCESS_DENIED);
            }
            return condition.toBuilder()
                    .ownerId(userId)
                    .build();
        }
        if (AuthConstants.MASTER.equals(role)) {
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
    // DB 커밋 완료 후 목록 캐시 무효화 - 롤백 시 불필요한 eviction 방지
    // 매장 데이터를 변경하는 @Transactional 메서드는 반드시 이 메서드를 호출해야 함
    private void evictListCacheAfterCommit() {
        TransactionUtils.runAfterCommit(storeListCacheRepository::evictAll);
    }

    /**
     * 매장 생성 이벤트를 Envelope으로 감싸 Outbox 테이블에 PENDING 상태로 저장
     * 실제 Kafka 발행은 StoreOutboxPublisher 스케줄러가 별도로 처리
     */
    private void saveStoreCreatedOutboxEvent(Store store) {
        StoreEventEnvelope<StoreCreatedEvent> envelope = storeEventFactory.createStoreCreatedEnvelope(UUID.randomUUID(), store);

        StoreOutboxEvent outboxEvent = StoreOutboxEvent.builder()
            .storeId(store.getStoreId())
            .eventType(StoreEventType.STORE_CREATED)
            .payload(serializeEnvelope(envelope))
            .build();

        storeOutboxEventRepository.save(outboxEvent);
    }

    // Envelope을 JSON 문자열로 직렬화 (Kafka 메시지로 발행될 형태)
    private String serializeEnvelope(Object envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new BaseException(StoreErrorCode.PAYLOAD_SERIALIZATION_FAILED);
        }
    }
}
