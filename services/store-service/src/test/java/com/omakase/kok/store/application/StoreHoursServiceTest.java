package com.omakase.kok.store.application;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.application.command.CreateStoreHoursBulkCommand;
import com.omakase.kok.store.application.command.CreateStoreHoursBulkCommand.HoursEntry;
import com.omakase.kok.store.application.command.UpdateStoreHoursCommand;
import com.omakase.kok.store.application.result.StoreHoursResult;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreCategory;
import com.omakase.kok.store.domain.entity.StoreHours;
import com.omakase.kok.store.domain.repository.StoreHoursRepository;
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

import java.lang.reflect.Field;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreHoursServiceTest {

    @Mock StoreHoursRepository storeHoursRepository;
    @Mock StoreFinder storeFinder;

    @InjectMocks
    StoreHoursService storeHoursService;

    private UUID ownerId;
    private UUID storeId;
    private Store store;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        storeId = UUID.randomUUID();
        store = Store.create(ownerId, StoreCategory.create("한식", 1, null), "테스트 매장", null,
                new Address("서울", "강남", null, null, null, null), null, null);
    }

    // createBulkHours

    @Test
    @DisplayName("영업일 등록 성공")
    void createBulk_operating_day_success() {
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(storeHoursRepository.findHoursByDay(any(), any())).thenReturn(Optional.empty());
        StoreHours saved = StoreHours.createOperating(store, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(21, 0), null, null);
        when(storeHoursRepository.save(any())).thenReturn(saved);

        CreateStoreHoursBulkCommand command = CreateStoreHoursBulkCommand.builder()
                .storeId(storeId).requesterId(ownerId)
                .hours(List.of(operatingEntry(DayOfWeek.MONDAY)))
                .build();

        List<StoreHoursResult> results = storeHoursService.createBulkHours(command);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isDayOff()).isFalse();
    }

    @Test
    @DisplayName("휴무일 등록 성공")
    void createBulk_day_off_success() {
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(storeHoursRepository.findHoursByDay(any(), any())).thenReturn(Optional.empty());
        StoreHours saved = StoreHours.createDayOff(store, DayOfWeek.SUNDAY);
        when(storeHoursRepository.save(any())).thenReturn(saved);

        CreateStoreHoursBulkCommand command = CreateStoreHoursBulkCommand.builder()
                .storeId(storeId).requesterId(ownerId)
                .hours(List.of(dayOffEntry(DayOfWeek.SUNDAY)))
                .build();

        List<StoreHoursResult> results = storeHoursService.createBulkHours(command);

        assertThat(results.get(0).isDayOff()).isTrue();
    }

    @Test
    @DisplayName("영업일인데 openTime 없으면 400")
    void createBulk_operating_without_open_time_throws() {
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);

        HoursEntry invalidEntry = HoursEntry.builder()
                .dayOfWeek(DayOfWeek.MONDAY)
                .isDayOff(false)
                .openTime(null)   // 영업일인데 openTime 없음
                .closeTime(LocalTime.of(21, 0))
                .build();

        CreateStoreHoursBulkCommand command = CreateStoreHoursBulkCommand.builder()
                .storeId(storeId).requesterId(ownerId)
                .hours(List.of(invalidEntry))
                .build();

        assertThatThrownBy(() -> storeHoursService.createBulkHours(command))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.INVALID_STORE_HOURS);
    }

    @Test
    @DisplayName("기존 soft delete 영업시간 재등록 시 restore 후 업데이트")
    void createBulk_restores_deleted_hours() {
        StoreHours deleted = StoreHours.createOperating(store, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0), null, null);
        deleted.delete(ownerId);

        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(storeHoursRepository.findHoursByDay(store, DayOfWeek.MONDAY)).thenReturn(Optional.of(deleted));
        when(storeHoursRepository.save(any())).thenReturn(deleted);

        CreateStoreHoursBulkCommand command = CreateStoreHoursBulkCommand.builder()
                .storeId(storeId).requesterId(ownerId)
                .hours(List.of(operatingEntry(DayOfWeek.MONDAY)))
                .build();

        storeHoursService.createBulkHours(command);

        assertThat(deleted.isDeleted()).isFalse(); // restore 확인
        assertThat(deleted.getOpenTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    @DisplayName("OWNER가 타인 매장 영업시간 등록 시 403")
    void createBulk_owner_cannot_register_to_others_store() {
        UUID otherId = UUID.randomUUID();
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);

        CreateStoreHoursBulkCommand command = CreateStoreHoursBulkCommand.builder()
                .storeId(storeId).requesterId(otherId)
                .hours(List.of(operatingEntry(DayOfWeek.MONDAY)))
                .build();

        assertThatThrownBy(() -> storeHoursService.createBulkHours(command))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.STORE_HOURS_ACCESS_DENIED);
    }

    // updateHours

    @Test
    @DisplayName("영업일로 변경 성공")
    void updateHours_to_operating_success() throws Exception {
        UUID hoursId = UUID.randomUUID();
        StoreHours hours = StoreHours.createDayOff(store, DayOfWeek.MONDAY);
        setHoursId(hours, hoursId);

        when(storeHoursRepository.findHours(storeId, hoursId)).thenReturn(Optional.of(hours));

        UpdateStoreHoursCommand command = UpdateStoreHoursCommand.builder()
                .storeId(storeId).hoursId(hoursId).requesterId(ownerId)
                .isDayOff(false)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(21, 0))
                .build();

        StoreHoursResult result = storeHoursService.updateHours(command);

        assertThat(result.isDayOff()).isFalse();
        assertThat(result.getOpenTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    @DisplayName("휴무일로 변경 시 openTime/closeTime null 처리")
    void updateHours_to_day_off_clears_times() throws Exception {
        UUID hoursId = UUID.randomUUID();
        StoreHours hours = StoreHours.createOperating(store, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(21, 0), null, null);
        setHoursId(hours, hoursId);

        when(storeHoursRepository.findHours(storeId, hoursId)).thenReturn(Optional.of(hours));

        UpdateStoreHoursCommand command = UpdateStoreHoursCommand.builder()
                .storeId(storeId).hoursId(hoursId).requesterId(ownerId)
                .isDayOff(true)
                .openTime(null).closeTime(null)
                .build();

        StoreHoursResult result = storeHoursService.updateHours(command);

        assertThat(result.isDayOff()).isTrue();
        assertThat(result.getOpenTime()).isNull();
        assertThat(result.getCloseTime()).isNull();
    }

    @Test
    @DisplayName("영업일로 수정인데 openTime 없으면 400")
    void updateHours_operating_without_time_throws() throws Exception {
        UUID hoursId = UUID.randomUUID();
        StoreHours hours = StoreHours.createDayOff(store, DayOfWeek.MONDAY);
        setHoursId(hours, hoursId);

        when(storeHoursRepository.findHours(storeId, hoursId)).thenReturn(Optional.of(hours));

        UpdateStoreHoursCommand command = UpdateStoreHoursCommand.builder()
                .storeId(storeId).hoursId(hoursId).requesterId(ownerId)
                .isDayOff(false)
                .openTime(null)
                .closeTime(LocalTime.of(21, 0))
                .build();

        assertThatThrownBy(() -> storeHoursService.updateHours(command))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.INVALID_STORE_HOURS);
    }

    @Test
    @DisplayName("OWNER가 타인 매장 영업시간 수정 시 403")
    void updateHours_owner_cannot_update_others() throws Exception {
        UUID hoursId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        StoreHours hours = StoreHours.createOperating(store, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(21, 0), null, null);
        setHoursId(hours, hoursId);

        when(storeHoursRepository.findHours(storeId, hoursId)).thenReturn(Optional.of(hours));

        UpdateStoreHoursCommand command = UpdateStoreHoursCommand.builder()
                .storeId(storeId).hoursId(hoursId).requesterId(otherId)
                .isDayOff(false)
                .openTime(LocalTime.of(10, 0)).closeTime(LocalTime.of(22, 0))
                .build();

        assertThatThrownBy(() -> storeHoursService.updateHours(command))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.STORE_HOURS_ACCESS_DENIED);
    }

    // deleteHours

    @Test
    @DisplayName("PREPARING 매장 영업시간 삭제 성공")
    void deleteHours_preparing_store_success() throws Exception {
        UUID hoursId = UUID.randomUUID();
        StoreHours hours = StoreHours.createOperating(store, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(21, 0), null, null);
        setHoursId(hours, hoursId);

        when(storeHoursRepository.findHours(storeId, hoursId)).thenReturn(Optional.of(hours));

        storeHoursService.deleteHours(storeId, hoursId, ownerId);

        assertThat(hours.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("OPEN 매장 영업시간 삭제 시 400")
    void deleteHours_open_store_throws() throws Exception {
        UUID hoursId = UUID.randomUUID();
        // OPEN 상태로 전환
        store.changeStatus(com.omakase.kok.store.domain.enums.StoreStatus.OPEN, ownerId);
        StoreHours hours = StoreHours.createOperating(store, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(21, 0), null, null);
        setHoursId(hours, hoursId);

        when(storeHoursRepository.findHours(storeId, hoursId)).thenReturn(Optional.of(hours));

        assertThatThrownBy(() -> storeHoursService.deleteHours(storeId, hoursId, ownerId))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.STORE_HOURS_CANNOT_DELETE_WHILE_OPEN);
    }

    @Test
    @DisplayName("존재하지 않는 영업시간 삭제 시 404")
    void deleteHours_not_found_throws() {
        UUID hoursId = UUID.randomUUID();
        when(storeHoursRepository.findHours(storeId, hoursId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeHoursService.deleteHours(storeId, hoursId, ownerId))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.STORE_HOURS_NOT_FOUND);
    }

    // helpers

    private HoursEntry operatingEntry(DayOfWeek day) {
        return HoursEntry.builder()
                .dayOfWeek(day)
                .isDayOff(false)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(21, 0))
                .build();
    }

    private HoursEntry dayOffEntry(DayOfWeek day) {
        return HoursEntry.builder()
                .dayOfWeek(day)
                .isDayOff(true)
                .build();
    }

    // @GeneratedValue는 JPA 영속 시점에만 동작하므로 단위 테스트에서는 리플렉션으로 주입
    private void setHoursId(StoreHours hours, UUID id) throws Exception {
        Field field = StoreHours.class.getDeclaredField("hoursId");
        field.setAccessible(true);
        field.set(hours, id);
    }
}
