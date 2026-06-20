package com.omakase.kok.store.application;

import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.application.command.AddStoreAmenityCommand;
import com.omakase.kok.store.application.result.StoreAmenityResult;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreAmenity;
import com.omakase.kok.store.domain.entity.StoreCategory;
import com.omakase.kok.store.domain.enums.AmenityType;
import com.omakase.kok.store.domain.repository.StoreAmenityRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreAmenityServiceTest {

    @Mock StoreAmenityRepository storeAmenityRepository;
    @Mock StoreFinder storeFinder;

    @InjectMocks
    StoreAmenityService storeAmenityService;

    private Store store;
    private UUID ownerId;
    private UUID storeId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        storeId = UUID.randomUUID();
        store = Store.create(ownerId, StoreCategory.create("한식", 1, null), "테스트 매장", null,
                new Address("서울", "강남", null, null, null, null), null, null);
    }

    @Test
    @DisplayName("요청에 없는 기존 편의시설은 soft delete")
    void syncAmenities_deletes_removed_types() {
        StoreAmenity existing = StoreAmenity.create(store, AmenityType.PARKING);

        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(storeAmenityRepository.findAllAmenitiesIncludingDeleted(store))
                .thenReturn(List.of(existing));
        doNothing().when(storeAmenityRepository).saveAll(any());

        // WIFI만 요청 → PARKING은 soft delete 대상
        AddStoreAmenityCommand command = AddStoreAmenityCommand.builder()
                .storeId(storeId)
                .requesterId(ownerId)
                .amenityTypes(List.of(AmenityType.WIFI))
                .build();

        StoreAmenityResult.Bulk result = storeAmenityService.syncAmenities(command);

        // 응답에는 WIFI만 포함, PARKING은 제외
        assertThat(result.getAmenities()).hasSize(1);
        assertThat(result.getAmenities().get(0).getAmenityType()).isEqualTo(AmenityType.WIFI);
        assertThat(existing.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("soft delete된 편의시설을 재요청하면 restore")
    void syncAmenities_restores_deleted_type() {
        StoreAmenity deleted = StoreAmenity.create(store, AmenityType.PARKING);
        deleted.delete(ownerId); // soft delete 상태로 설정
        assertThat(deleted.isDeleted()).isTrue();

        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(storeAmenityRepository.findAllAmenitiesIncludingDeleted(store))
                .thenReturn(List.of(deleted));
        doNothing().when(storeAmenityRepository).saveAll(any());

        AddStoreAmenityCommand command = AddStoreAmenityCommand.builder()
                .storeId(storeId)
                .requesterId(ownerId)
                .amenityTypes(List.of(AmenityType.PARKING))
                .build();

        StoreAmenityResult.Bulk result = storeAmenityService.syncAmenities(command);

        assertThat(deleted.isDeleted()).isFalse(); // restore 확인
        assertThat(result.getAmenities()).hasSize(1);
    }

    @Test
    @DisplayName("기존에 없는 편의시설 타입은 신규 생성")
    void syncAmenities_creates_new_type() {
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(storeAmenityRepository.findAllAmenitiesIncludingDeleted(store))
                .thenReturn(List.of()); // 기존 편의시설 없음
        doNothing().when(storeAmenityRepository).saveAll(any());

        AddStoreAmenityCommand command = AddStoreAmenityCommand.builder()
                .storeId(storeId)
                .requesterId(ownerId)
                .amenityTypes(List.of(AmenityType.WIFI, AmenityType.PARKING))
                .build();

        StoreAmenityResult.Bulk result = storeAmenityService.syncAmenities(command);

        assertThat(result.getAmenities()).hasSize(2);
    }

    @Test
    @DisplayName("빈 목록을 요청하면 기존 편의시설 전체 soft delete")
    void syncAmenities_with_empty_request_deletes_all() {
        StoreAmenity wifi = StoreAmenity.create(store, AmenityType.WIFI);
        StoreAmenity parking = StoreAmenity.create(store, AmenityType.PARKING);

        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(storeAmenityRepository.findAllAmenitiesIncludingDeleted(store))
                .thenReturn(List.of(wifi, parking));
        doNothing().when(storeAmenityRepository).saveAll(any());

        AddStoreAmenityCommand command = AddStoreAmenityCommand.builder()
                .storeId(storeId)
                .requesterId(ownerId)
                .amenityTypes(List.of())
                .build();

        StoreAmenityResult.Bulk result = storeAmenityService.syncAmenities(command);

        assertThat(wifi.isDeleted()).isTrue();
        assertThat(parking.isDeleted()).isTrue();
        assertThat(result.getAmenities()).isEmpty();
    }

    // deleteAmenity

    @Test
    @DisplayName("OWNER 본인은 편의시설 삭제 가능")
    void deleteAmenity_owner_can_delete() {
        UUID amenityId = UUID.randomUUID();
        StoreAmenity amenity = StoreAmenity.create(store, AmenityType.PARKING);

        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(storeAmenityRepository.findAmenity(storeId, amenityId)).thenReturn(Optional.of(amenity));

        storeAmenityService.deleteAmenity(storeId, amenityId, ownerId, "OWNER");

        assertThat(amenity.isDeleted()).isTrue();
        assertThat(amenity.getDeletedBy()).isEqualTo(ownerId);
    }

    @Test
    @DisplayName("MASTER는 소유자 검증 없이 편의시설 삭제 가능")
    void deleteAmenity_master_skips_owner_check() {
        UUID amenityId = UUID.randomUUID();
        UUID masterId = UUID.randomUUID();
        StoreAmenity amenity = StoreAmenity.create(store, AmenityType.PARKING);

        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(storeAmenityRepository.findAmenity(storeId, amenityId)).thenReturn(Optional.of(amenity));

        storeAmenityService.deleteAmenity(storeId, amenityId, masterId, AuthConstants.MASTER);

        assertThat(amenity.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("OWNER가 타인 매장 편의시설 삭제 시 403")
    void deleteAmenity_owner_cannot_delete_others() {
        UUID amenityId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);

        assertThatThrownBy(() -> storeAmenityService.deleteAmenity(storeId, amenityId, otherId, "OWNER"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.AMENITY_ACCESS_DENIED);
    }

    @Test
    @DisplayName("이미 삭제된 편의시설 삭제 시 400")
    void deleteAmenity_already_deleted_throws() {
        UUID amenityId = UUID.randomUUID();
        StoreAmenity amenity = StoreAmenity.create(store, AmenityType.PARKING);
        amenity.delete(ownerId);

        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(storeAmenityRepository.findAmenity(storeId, amenityId)).thenReturn(Optional.of(amenity));

        assertThatThrownBy(() -> storeAmenityService.deleteAmenity(storeId, amenityId, ownerId, "OWNER"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.AMENITY_ALREADY_DELETED);
    }
}
