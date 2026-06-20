package com.omakase.kok.store.application;

import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.application.command.ChangeStoreStatusCommand;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreCategory;
import com.omakase.kok.store.domain.enums.StoreStatus;
import com.omakase.kok.store.domain.repository.StoreAmenityRepository;
import com.omakase.kok.store.domain.repository.StoreCategoryRepository;
import com.omakase.kok.store.domain.repository.StoreHoursRepository;
import com.omakase.kok.store.domain.repository.StoreImageRepository;
import com.omakase.kok.store.domain.repository.StoreRepository;
import com.omakase.kok.store.domain.service.StoreFinder;
import com.omakase.kok.store.domain.vo.Address;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock StoreRepository storeRepository;
    @Mock StoreCategoryRepository storeCategoryRepository;
    @Mock StoreHoursRepository storeHoursRepository;
    @Mock StoreAmenityRepository storeAmenityRepository;
    @Mock StoreImageRepository storeImageRepository;
    @Mock StoreFinder storeFinder;

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
}
