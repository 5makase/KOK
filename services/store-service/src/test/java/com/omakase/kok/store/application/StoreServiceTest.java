package com.omakase.kok.store.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.common.exception.CommonErrorCode;
import com.omakase.kok.store.application.cache.StoreListCacheRepository;
import com.omakase.kok.store.application.command.ChangeStoreStatusCommand;
import com.omakase.kok.store.application.command.CreateStoreCommand;
import com.omakase.kok.store.application.command.UpdateStoreCommand;
import com.omakase.kok.store.application.result.StoreResult;
import com.omakase.kok.store.application.result.StoreSummaryResult;
import com.omakase.kok.store.application.port.OwnerApprovalPort;
import com.omakase.kok.store.application.validator.StoreOwnerValidator;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreCategory;
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
import com.omakase.kok.store.domain.vo.Address;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import com.omakase.kok.store.infrastructure.kafka.event.StoreCreatedEvent;
import com.omakase.kok.store.infrastructure.kafka.event.StoreEventEnvelope;
import com.omakase.kok.store.infrastructure.kafka.event.StoreEventFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock StoreRepository storeRepository;
    @Mock StoreCategoryRepository storeCategoryRepository;
    @Mock StoreHoursRepository storeHoursRepository;
    @Mock StoreAmenityRepository storeAmenityRepository;
    @Mock StoreImageRepository storeImageRepository;
    @Mock MenuRepository menuRepository;
    @Mock StoreListCacheRepository storeListCacheRepository;
    @Mock StoreFinder storeFinder;
    @Mock StoreOutboxEventRepository storeOutboxEventRepository;
    @Mock StoreEventFactory storeEventFactory;
    @Mock OwnerApprovalPort ownerApprovalPort;
    @Spy StoreOwnerValidator storeOwnerValidator = new StoreOwnerValidator();
    @Spy ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    StoreService storeService;

    private UUID ownerId;
    private UUID storeId;
    private Store store;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        storeId = UUID.randomUUID();
        StoreCategory category = StoreCategory.create("한식", 1, null);
        store = Store.create(ownerId, category, "테스트 매장", null,
            new Address("서울특별시", "강남구", null, null, null, null), null, null);
    }

    // deleteStore

    @Test
    @DisplayName("MASTER는 소유자 검증 없이 매장 삭제 가능")
    void deleteStore_master_skips_owner_check() {
        UUID masterId = UUID.randomUUID();
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);

        // masterId가 ownerId와 다르지만 MASTER 권한이므로 삭제 가능
        assertThatCode(() -> storeService.deleteStore(storeId, masterId, AuthConstants.MASTER))
            .doesNotThrowAnyException();
        assertThat(store.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("OWNER 본인은 매장 삭제 가능")
    void deleteStore_owner_can_delete_own_store() {
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);

        assertThatCode(() -> storeService.deleteStore(storeId, ownerId, "OWNER"))
            .doesNotThrowAnyException();
        assertThat(store.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("OWNER가 타인 매장 삭제 시 403")
    void deleteStore_owner_cannot_delete_others_store() {
        UUID otherId = UUID.randomUUID();
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);

        assertThatThrownBy(() -> storeService.deleteStore(storeId, otherId, "OWNER"))
            .isInstanceOf(BaseException.class)
            .extracting(e -> ((BaseException) e).getErrorCode())
            .isEqualTo(StoreErrorCode.STORE_ACCESS_DENIED);
    }

    // changeStatus

    @Test
    @DisplayName("OPEN 전환 시 영업시간 7일치 미등록이면 예외")
    void changeStatus_open_requires_hours() {
        Store preparingStore = Store.create(ownerId,
            StoreCategory.create("한식", 1, null), "이름", null,
            new Address("서울", "강남", null, null, null, null), null, null);

        UUID preparingStoreId = UUID.randomUUID();
        when(storeFinder.findActiveOrThrow(preparingStoreId)).thenReturn(preparingStore);
        when(storeHoursRepository.countRegisteredHours(preparingStore)).thenReturn(5L);

        ChangeStoreStatusCommand command = ChangeStoreStatusCommand.builder()
            .storeId(preparingStoreId)
            .requesterId(ownerId)
            .status(StoreStatus.OPEN)
            .role("OWNER")
            .build();

        assertThatThrownBy(() -> storeService.changeStatus(command))
            .isInstanceOf(BaseException.class)
            .extracting(e -> ((BaseException) e).getErrorCode())
            .isEqualTo(StoreErrorCode.STORE_HOURS_REQUIRED_FOR_OPEN);
    }

    @Test
    @DisplayName("MASTER는 소유자 검증 없이 상태 변경 가능")
    void changeStatus_master_skips_owner_check() {
        UUID masterId = UUID.randomUUID();
        UUID masterStoreId = UUID.randomUUID();
        Store masterStore = Store.create(ownerId,
            StoreCategory.create("한식", 1, null), "이름", null,
            new Address("서울", "강남", null, null, null, null), null, null);

        when(storeFinder.findActiveOrThrow(masterStoreId)).thenReturn(masterStore);
        when(storeHoursRepository.countRegisteredHours(masterStore)).thenReturn(7L);

        ChangeStoreStatusCommand command = ChangeStoreStatusCommand.builder()
            .storeId(masterStoreId)
            .requesterId(masterId) // ownerId 아님
            .status(StoreStatus.OPEN)
            .role(AuthConstants.MASTER)
            .build();

        // MASTER라서 validateOwner 스킵 → 정상 처리
        assertThatCode(() -> storeService.changeStatus(command))
            .doesNotThrowAnyException();
        assertThat(masterStore.getStatus()).isEqualTo(StoreStatus.OPEN);
    }

    // createStore

    @Test
    @DisplayName("미승인 OWNER 매장 등록 시 403")
    void createStore_owner_not_approved_throws() {
        when(ownerApprovalPort.isApproved(ownerId)).thenReturn(false);

        CreateStoreCommand command = CreateStoreCommand.builder()
            .ownerId(ownerId).categoryId(UUID.randomUUID())
            .name("테스트 매장").phone(null)
            .address(new Address("서울", "강남", null, null, null, null))
            .description(null).maxCapacity(null)
            .build();

        assertThatThrownBy(() -> storeService.createStore(command))
            .isInstanceOf(BaseException.class)
            .extracting(e -> ((BaseException) e).getErrorCode())
            .isEqualTo(StoreErrorCode.OWNER_NOT_APPROVED);
    }

    @Test
    @DisplayName("승인 상태 조회 실패(서킷브레이커, 타임아웃) 시 503 예외 전파")
    void createStore_approval_check_failed_propagates() {
        when(ownerApprovalPort.isApproved(ownerId))
            .thenThrow(new BaseException(StoreErrorCode.OWNER_APPROVAL_CHECK_FAILED));

        CreateStoreCommand command = CreateStoreCommand.builder()
            .ownerId(ownerId).categoryId(UUID.randomUUID())
            .name("테스트 매장").phone(null)
            .address(new Address("서울", "강남", null, null, null, null))
            .description(null).maxCapacity(null)
            .build();

        assertThatThrownBy(() -> storeService.createStore(command))
            .isInstanceOf(BaseException.class)
            .extracting(e -> ((BaseException) e).getErrorCode())
            .isEqualTo(StoreErrorCode.OWNER_APPROVAL_CHECK_FAILED);
    }

    @Test
    @DisplayName("소분류 카테고리로 매장 등록 성공")
    void createStore_with_sub_category_success() {
        UUID categoryId = UUID.randomUUID();
        StoreCategory root = StoreCategory.create("한식", 1, null);
        StoreCategory sub = StoreCategory.create("국밥", 1, root);

        when(ownerApprovalPort.isApproved(ownerId)).thenReturn(true);
        when(storeCategoryRepository.findCategory(categoryId)).thenReturn(java.util.Optional.of(sub));
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storeEventFactory.createStoreCreatedEnvelope(any(UUID.class), any(Store.class)))
            .thenAnswer(inv -> {
                Store savedStore = inv.getArgument(1, Store.class);
                return new StoreEventEnvelope<>(
                    inv.getArgument(0, UUID.class),
                    StoreEventType.STORE_CREATED.name(),
                    1,
                    Instant.now(),
                    "store-service",
                    new StoreCreatedEvent(savedStore.getStoreId())
                );
            });

        CreateStoreCommand command = CreateStoreCommand.builder()
            .ownerId(ownerId).categoryId(categoryId)
            .name("테스트 매장").phone(null)
            .address(new Address("서울", "강남", null, null, null, null))
            .description(null).maxCapacity(null)
            .build();

        StoreResult result = storeService.createStore(command);

        assertThat(result.getName()).isEqualTo("테스트 매장");
        assertThat(result.getStatus()).isEqualTo(StoreStatus.PREPARING.name()); // Result DTO는 String 변환 적용
        // 매장 저장과 같은 트랜잭션에서 Outbox 이벤트가 저장되는지 검증
        verify(storeOutboxEventRepository).save(any(StoreOutboxEvent.class));
    }

    @Test
    @DisplayName("대분류 카테고리로 매장 등록 시 400")
    void createStore_with_root_category_throws() {
        UUID categoryId = UUID.randomUUID();
        StoreCategory root = StoreCategory.create("한식", 1, null); // 대분류

        when(ownerApprovalPort.isApproved(ownerId)).thenReturn(true);
        when(storeCategoryRepository.findCategory(categoryId)).thenReturn(java.util.Optional.of(root));

        CreateStoreCommand command = CreateStoreCommand.builder()
            .ownerId(ownerId).categoryId(categoryId)
            .name("테스트 매장").phone(null)
            .address(new Address("서울", "강남", null, null, null, null))
            .description(null).maxCapacity(null)
            .build();

        assertThatThrownBy(() -> storeService.createStore(command))
            .isInstanceOf(com.omakase.kok.common.exception.BaseException.class)
            .extracting(e -> ((com.omakase.kok.common.exception.BaseException) e).getErrorCode())
            .isEqualTo(StoreErrorCode.INVALID_CATEGORY);
    }

    // updateStore

    @Test
    @DisplayName("OWNER가 타인 매장 수정 시 403")
    void updateStore_owner_cannot_update_others() {
        UUID otherId = UUID.randomUUID();
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);

        UpdateStoreCommand command = UpdateStoreCommand.builder()
            .storeId(storeId).requesterId(otherId)
            .name("변경").build();

        assertThatThrownBy(() -> storeService.updateStore(command, AuthConstants.OWNER))
            .isInstanceOf(com.omakase.kok.common.exception.BaseException.class)
            .extracting(e -> ((com.omakase.kok.common.exception.BaseException) e).getErrorCode())
            .isEqualTo(StoreErrorCode.STORE_ACCESS_DENIED);
    }

    @Test
    @DisplayName("updateStore - categoryId null이면 기존 카테고리 유지")
    void updateStore_null_category_keeps_existing() {
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);

        UpdateStoreCommand command = UpdateStoreCommand.builder()
            .storeId(storeId).requesterId(ownerId)
            .name("변경된 이름").categoryId(null)
            .build();

        StoreResult result = storeService.updateStore(command, AuthConstants.OWNER);

        assertThat(result.getName()).isEqualTo("변경된 이름");
        assertThat(store.getCategory().getName()).isEqualTo("한식"); // 카테고리 유지
    }

    // searchStores

    @Test
    @DisplayName("OWNER 역할 - 캐시 바이패스, DB 직접 조회")
    void searchStores_owner_bypasses_cache() {
        UUID ownerId = UUID.randomUUID();
        StoreSearchCondition condition = StoreSearchCondition.builder().build();
        PageRequest pageable = PageRequest.of(0, 20);
        Page<Store> emptyPage = new PageImpl<>(List.of());

        when(storeRepository.search(any(), eq(pageable))).thenReturn(emptyPage);

        storeService.searchStores(condition, null, ownerId, "OWNER", pageable);

        verify(storeListCacheRepository, never()).get(any(), any());
        verify(storeListCacheRepository, never()).set(any(), any(), any());
        verify(storeRepository).search(any(), eq(pageable));
    }

    @Test
    @DisplayName("캐시 히트 - DB 조회 생략")
    void searchStores_cache_hit_skips_db() {
        StoreSearchCondition condition = StoreSearchCondition.builder().build();
        PageRequest pageable = PageRequest.of(0, 20);
        Page<StoreResult> cachedPage = new PageImpl<>(List.of());

        when(storeListCacheRepository.get(any(), eq(pageable))).thenReturn(Optional.of(cachedPage));

        Page<StoreResult> result = storeService.searchStores(condition, null, null, "USER", pageable);

        assertThat(result).isEqualTo(cachedPage);
        verify(storeRepository, never()).search(any(), any());
        verify(storeListCacheRepository, never()).set(any(), any(), any());
    }

    @Test
    @DisplayName("캐시 미스 - DB 조회 후 캐시 저장")
    void searchStores_cache_miss_queries_db_and_caches() {
        StoreSearchCondition condition = StoreSearchCondition.builder().build();
        PageRequest pageable = PageRequest.of(0, 20);
        Page<Store> dbPage = new PageImpl<>(List.of());

        when(storeListCacheRepository.get(any(), eq(pageable))).thenReturn(Optional.empty());
        when(storeRepository.search(any(), eq(pageable))).thenReturn(dbPage);

        storeService.searchStores(condition, null, null, "USER", pageable);

        verify(storeRepository).search(any(), eq(pageable));
        verify(storeListCacheRepository).set(any(), eq(pageable), any());
    }

    @Test
    @DisplayName("MASTER 역할 - 조건 그대로 통과 (status 강제 없음)")
    void searchStores_master_passes_condition_as_is() {
        StoreSearchCondition condition = StoreSearchCondition.builder()
            .status(StoreStatus.PREPARING).build();
        PageRequest pageable = PageRequest.of(0, 20);
        Page<Store> emptyPage = new PageImpl<>(List.of());

        when(storeListCacheRepository.get(any(), eq(pageable))).thenReturn(Optional.empty());
        when(storeRepository.search(any(), eq(pageable))).thenReturn(emptyPage);

        storeService.searchStores(condition, null, UUID.randomUUID(), AuthConstants.MASTER, pageable);

        // MASTER는 OPEN 강제 없이 PREPARING 그대로 전달
        verify(storeRepository).search(
            argThat(c -> c.getStatus() == StoreStatus.PREPARING),
            eq(pageable)
        );
    }

    @Test
    @DisplayName("USER 역할 - status OPEN 강제 적용")
    void searchStores_user_forces_open_status() {
        StoreSearchCondition condition = StoreSearchCondition.builder().build();
        PageRequest pageable = PageRequest.of(0, 20);
        Page<Store> emptyPage = new PageImpl<>(List.of());

        when(storeListCacheRepository.get(any(), eq(pageable))).thenReturn(Optional.empty());
        when(storeRepository.search(any(), eq(pageable))).thenReturn(emptyPage);

        storeService.searchStores(condition, null, null, "USER", pageable);

        // resolveCondition이 status=OPEN으로 설정했는지 검증
        verify(storeRepository).search(
            argThat(c -> c.getStatus() == StoreStatus.OPEN),
            eq(pageable)
        );
    }

    // getStore

    @Test
    @DisplayName("MASTER는 삭제된 매장도 조회 가능")
    void getStore_master_can_read_deleted_store() {
        store.delete(ownerId);
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(store));
        when(storeHoursRepository.findTodayHours(eq(storeId), any(DayOfWeek.class)))
            .thenReturn(Optional.empty());
        when(storeAmenityRepository.findAllAmenities(store)).thenReturn(List.of());
        when(storeImageRepository.findImagePreview(storeId)).thenReturn(List.of());
        when(menuRepository.findAllMenus(store)).thenReturn(List.of());

        StoreResult result = storeService.getStore(storeId, AuthConstants.MASTER);

        assertThat(result.getName()).isEqualTo("테스트 매장");
        // MASTER 경로: storeRepository.findById 사용 (storeFinder 미사용)
        verify(storeRepository).findById(storeId);
    }

    @Test
    @DisplayName("USER는 삭제된(비활성) 매장 조회 시 예외")
    void getStore_user_cannot_read_inactive_store() {
        when(storeFinder.findActiveOrThrow(storeId))
            .thenThrow(new BaseException(StoreErrorCode.STORE_NOT_FOUND));

        assertThatThrownBy(() -> storeService.getStore(storeId, "USER"))
            .isInstanceOf(BaseException.class)
            .extracting(e -> ((BaseException) e).getErrorCode())
            .isEqualTo(StoreErrorCode.STORE_NOT_FOUND);
    }

    @Test
    @DisplayName("오늘 영업시간이 없으면 todayHours null로 결과 반환")
    void getStore_no_today_hours_returns_result_without_hours() {
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(storeHoursRepository.findTodayHours(eq(storeId), any(DayOfWeek.class)))
            .thenReturn(Optional.empty());
        when(storeAmenityRepository.findAllAmenities(store)).thenReturn(List.of());
        when(storeImageRepository.findImagePreview(storeId)).thenReturn(List.of());
        when(menuRepository.findAllMenus(store)).thenReturn(List.of());

        StoreResult result = storeService.getStore(storeId, "USER");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("테스트 매장");
    }

    // getStoreSummary

    @Test
    @DisplayName("getStoreSummary - 활성 매장 요약 정보 반환")
    void getStoreSummary_returns_summary() {
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);

        StoreSummaryResult result = storeService.getStoreSummary(storeId);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("OWNER인데 X-User-Id 누락(null)이면 403 - 전체 매장 조회 차단")
    void searchStores_owner_with_null_userId_throws() {
        StoreSearchCondition condition = StoreSearchCondition.builder().build();
        PageRequest pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> storeService.searchStores(condition, null, null, AuthConstants.OWNER, pageable))
            .isInstanceOf(BaseException.class)
            .extracting(e -> ((BaseException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("getStoreSummary - 비활성 매장이면 예외")
    void getStoreSummary_inactive_store_throws() {
        when(storeFinder.findActiveOrThrow(storeId))
            .thenThrow(new BaseException(StoreErrorCode.STORE_NOT_FOUND));

        assertThatThrownBy(() -> storeService.getStoreSummary(storeId))
            .isInstanceOf(BaseException.class)
            .extracting(e -> ((BaseException) e).getErrorCode())
            .isEqualTo(StoreErrorCode.STORE_NOT_FOUND);
    }

    // resolveCategoryIds (searchStores 경유 테스트)

    @Test
    @DisplayName("categoryId null이면 categoryIds 미주입 - 전체 조회")
    void resolveCategoryIds_null_categoryId_skips_filter() {
        StoreSearchCondition condition = StoreSearchCondition.builder().build();
        PageRequest pageable = PageRequest.of(0, 10);
        when(storeListCacheRepository.get(any(), any())).thenReturn(Optional.empty());
        when(storeRepository.search(any(), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        storeService.searchStores(condition, null, null, "USER", pageable);

        verify(storeRepository).search(
            argThat(c -> c.getCategoryIds() == null),
            any()
        );
        verify(storeCategoryRepository, never()).findCategory(any());
    }

    @Test
    @DisplayName("소분류 categoryId → categoryIds=[categoryId] 단일 목록 주입")
    void resolveCategoryIds_subCategory_injects_single_id() {
        UUID subId = UUID.randomUUID();
        StoreCategory root = StoreCategory.create("한식", 1, null);
        StoreCategory sub = StoreCategory.create("국밥", 1, root);

        when(storeCategoryRepository.findCategory(subId)).thenReturn(Optional.of(sub));
        when(storeListCacheRepository.get(any(), any())).thenReturn(Optional.empty());
        when(storeRepository.search(any(), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        storeService.searchStores(StoreSearchCondition.builder().build(), subId, null, "USER", PageRequest.of(0, 10));

        verify(storeRepository).search(
            argThat(c -> c.getCategoryIds() != null && c.getCategoryIds().equals(List.of(subId))),
            any()
        );
    }

    @Test
    @DisplayName("대분류 categoryId → 활성 소분류 ID 목록으로 확장")
    void resolveCategoryIds_parentCategory_expands_to_active_children() throws Exception {
        UUID parentId = UUID.randomUUID();
        StoreCategory root = StoreCategory.create("한식", 1, null);
        StoreCategory child1 = StoreCategory.create("국밥", 1, root);
        StoreCategory child2 = StoreCategory.create("찌개", 2, root);
        setChildren(root, List.of(child1, child2));

        when(storeCategoryRepository.findCategory(parentId)).thenReturn(Optional.of(root));
        when(storeListCacheRepository.get(any(), any())).thenReturn(Optional.empty());
        when(storeRepository.search(any(), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        storeService.searchStores(StoreSearchCondition.builder().build(), parentId, null, "USER", PageRequest.of(0, 10));

        verify(storeRepository).search(
            argThat(c -> c.getCategoryIds() != null && c.getCategoryIds().size() == 2),
            any()
        );
    }

    @Test
    @DisplayName("대분류의 모든 소분류가 soft delete → categoryIds 빈 리스트 주입 → 결과 없음")
    void resolveCategoryIds_allChildrenDeleted_injects_empty_list() throws Exception {
        UUID parentId = UUID.randomUUID();
        StoreCategory root = StoreCategory.create("한식", 1, null);
        StoreCategory deletedChild = StoreCategory.create("폐지된국밥", 1, root);
        deletedChild.delete(UUID.randomUUID()); // soft delete
        setChildren(root, List.of(deletedChild));

        when(storeCategoryRepository.findCategory(parentId)).thenReturn(Optional.of(root));
        when(storeListCacheRepository.get(any(), any())).thenReturn(Optional.empty());
        when(storeRepository.search(any(), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        storeService.searchStores(StoreSearchCondition.builder().build(), parentId, null, "USER", PageRequest.of(0, 10));

        // 빈 리스트 → inCategories()에서 Expressions.FALSE → 결과 없음
        verify(storeRepository).search(
            argThat(c -> c.getCategoryIds() != null && c.getCategoryIds().isEmpty()),
            any()
        );
    }

    @Test
    @DisplayName("존재하지 않는 categoryId → CATEGORY_NOT_FOUND 예외")
    void resolveCategoryIds_notFound_throws() {
        UUID unknownId = UUID.randomUUID();
        when(storeCategoryRepository.findCategory(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            storeService.searchStores(StoreSearchCondition.builder().build(), unknownId, null, "USER", PageRequest.of(0, 10)))
            .isInstanceOf(BaseException.class)
            .extracting(e -> ((BaseException) e).getErrorCode())
            .isEqualTo(StoreErrorCode.CATEGORY_NOT_FOUND);
    }

    // StoreCategory.children은 JPA 관리 컬렉션(setter 없음) → reflection으로 주입
    private void setChildren(StoreCategory parent, List<StoreCategory> children) throws Exception {
        Field field = StoreCategory.class.getDeclaredField("children");
        field.setAccessible(true);
        field.set(parent, new ArrayList<>(children));
    }
}