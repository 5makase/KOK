package com.omakase.kok.store.application;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.application.command.UpdateStoreImageCommand;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreCategory;
import com.omakase.kok.store.domain.entity.StoreImage;
import com.omakase.kok.store.domain.repository.StoreImageRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreImageServiceTest {

    @Mock StoreImageRepository storeImageRepository;
    @Mock StoreFinder storeFinder;

    @InjectMocks
    StoreImageService storeImageService;

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
    @DisplayName("displayOrder 변경 시 목표 슬롯에 활성 이미지가 있으면 soft delete")
    void updateImage_evicts_active_slot_image() throws Exception {
        UUID imageId = UUID.randomUUID();
        StoreImage target = StoreImage.create(store, "url1", 1); // 변경 대상 이미지 (슬롯 1)
        StoreImage occupying = StoreImage.create(store, "url2", 2); // 슬롯 2에 있는 이미지
        setImageId(target, imageId); // JPA 없이 실행되므로 @GeneratedValue 미작동 → 수동 주입
        setImageId(occupying, UUID.randomUUID());

        when(storeImageRepository.findImage(storeId, imageId)).thenReturn(Optional.of(target));
        when(storeImageRepository.findImageByDisplayOrder(storeId, 2)).thenReturn(Optional.of(occupying));

        UpdateStoreImageCommand command = UpdateStoreImageCommand.builder()
                .storeId(storeId)
                .imageId(imageId)
                .requesterId(ownerId)
                .imageUrl("url1-updated")
                .displayOrder(2) // 슬롯 2로 이동
                .build();

        storeImageService.updateImage(command);

        assertThat(occupying.isDeleted()).isTrue(); // 슬롯 2의 기존 이미지 soft delete
    }

    @Test
    @DisplayName("목표 슬롯에 이미 삭제된 이미지가 있으면 delete() 재호출 안 함")
    void updateImage_skips_already_deleted_slot_image() throws Exception {
        UUID imageId = UUID.randomUUID();
        StoreImage target = StoreImage.create(store, "url1", 1);
        StoreImage alreadyDeleted = StoreImage.create(store, "url2", 2);
        setImageId(target, imageId);
        setImageId(alreadyDeleted, UUID.randomUUID());
        alreadyDeleted.delete(ownerId); // 이미 삭제된 상태

        when(storeImageRepository.findImage(storeId, imageId)).thenReturn(Optional.of(target));
        when(storeImageRepository.findImageByDisplayOrder(storeId, 2)).thenReturn(Optional.of(alreadyDeleted));

        UpdateStoreImageCommand command = UpdateStoreImageCommand.builder()
                .storeId(storeId)
                .imageId(imageId)
                .requesterId(ownerId)
                .imageUrl("url1-updated")
                .displayOrder(2)
                .build();

        storeImageService.updateImage(command);

        // deletedAt이 최초 delete() 시각 그대로 - 재호출되지 않음을 간접 확인
        assertThat(alreadyDeleted.isDeleted()).isTrue();
        assertThat(alreadyDeleted.getDeletedBy()).isEqualTo(ownerId);
    }

    @Test
    @DisplayName("이미 삭제된 이미지 수정 시 예외")
    void updateImage_on_deleted_image_throws() {
        UUID imageId = UUID.randomUUID();
        StoreImage deleted = StoreImage.create(store, "url1", 1);
        deleted.delete(ownerId);

        when(storeImageRepository.findImage(storeId, imageId)).thenReturn(Optional.of(deleted));

        UpdateStoreImageCommand command = UpdateStoreImageCommand.builder()
                .storeId(storeId)
                .imageId(imageId)
                .requesterId(ownerId)
                .imageUrl("new-url")
                .displayOrder(1)
                .build();

        assertThatThrownBy(() -> storeImageService.updateImage(command))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.STORE_IMAGE_ALREADY_DELETED);
    }

    // @GeneratedValue는 JPA 영속 시점에만 동작하므로 단위 테스트에서는 리플렉션으로 주입
    private void setImageId(StoreImage image, UUID id) throws Exception {
        Field field = StoreImage.class.getDeclaredField("imageId");
        field.setAccessible(true);
        field.set(image, id);
    }
}
