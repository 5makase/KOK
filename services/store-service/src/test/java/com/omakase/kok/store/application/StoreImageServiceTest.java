package com.omakase.kok.store.application;

import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.application.validator.StoreOwnerValidator;
import org.mockito.Spy;
import com.omakase.kok.store.application.command.AddStoreImageCommand;
import com.omakase.kok.store.application.command.AddStoreImageCommand.ImageEntry;
import com.omakase.kok.store.application.command.UpdateStoreImageCommand;
import com.omakase.kok.store.application.result.StoreImageResult;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreImageServiceTest {

    @Mock StoreImageRepository storeImageRepository;
    @Mock StoreFinder storeFinder;
    @Spy StoreOwnerValidator storeOwnerValidator = new StoreOwnerValidator();

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

    // addImages

    @Test
    @DisplayName("이미지 등록 성공 - 신규 슬롯")
    void addImages_new_slot_success() {
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(storeImageRepository.findAllByDisplayOrders(any(), anyList())).thenReturn(List.of());
        StoreImage saved = StoreImage.create(store, "url1", 1);
        when(storeImageRepository.saveAll(anyList())).thenReturn(List.of(saved));

        AddStoreImageCommand command = AddStoreImageCommand.builder()
                .storeId(storeId).requesterId(ownerId)
                .images(List.of(ImageEntry.builder().imageUrl("url1").displayOrder(1).build()))
                .build();

        List<StoreImageResult> results = storeImageService.addImages(command, AuthConstants.OWNER);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDisplayOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("요청 내 displayOrder 중복 시 409")
    void addImages_duplicate_display_order_in_request_throws() {
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);

        AddStoreImageCommand command = AddStoreImageCommand.builder()
                .storeId(storeId).requesterId(ownerId)
                .images(List.of(
                        ImageEntry.builder().imageUrl("url1").displayOrder(1).build(),
                        ImageEntry.builder().imageUrl("url2").displayOrder(1).build() // 중복
                ))
                .build();

        assertThatThrownBy(() -> storeImageService.addImages(command, AuthConstants.OWNER))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.STORE_IMAGE_DUPLICATE_DISPLAY_ORDER);
    }

    @Test
    @DisplayName("OWNER가 타인 매장에 이미지 등록 시 403")
    void addImages_owner_cannot_add_to_others_store() {
        UUID otherId = UUID.randomUUID();
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);

        AddStoreImageCommand command = AddStoreImageCommand.builder()
                .storeId(storeId).requesterId(otherId)
                .images(List.of(ImageEntry.builder().imageUrl("url1").displayOrder(1).build()))
                .build();

        assertThatThrownBy(() -> storeImageService.addImages(command, AuthConstants.OWNER))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.STORE_IMAGE_ACCESS_DENIED);
    }

    @Test
    @DisplayName("soft delete된 슬롯 재등록 시 restore 후 업데이트")
    void addImages_restores_deleted_slot() {
        StoreImage deleted = StoreImage.create(store, "old-url", 1);
        deleted.delete(ownerId);

        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(storeImageRepository.findAllByDisplayOrders(any(), anyList())).thenReturn(List.of(deleted));
        when(storeImageRepository.saveAll(anyList())).thenReturn(List.of(deleted));

        AddStoreImageCommand command = AddStoreImageCommand.builder()
                .storeId(storeId).requesterId(ownerId)
                .images(List.of(ImageEntry.builder().imageUrl("new-url").displayOrder(1).build()))
                .build();

        storeImageService.addImages(command, AuthConstants.OWNER);

        assertThat(deleted.isDeleted()).isFalse();
        assertThat(deleted.getImageUrl()).isEqualTo("new-url");
    }

    // deleteImage

    @Test
    @DisplayName("이미지 삭제 성공")
    void deleteImage_success() throws Exception {
        UUID imageId = UUID.randomUUID();
        StoreImage image = StoreImage.create(store, "url1", 1);
        setImageId(image, imageId);

        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(storeImageRepository.findImageById(storeId, imageId)).thenReturn(Optional.of(image));

        assertThatCode(() -> storeImageService.deleteImage(storeId, imageId, ownerId, AuthConstants.OWNER))
                .doesNotThrowAnyException();
        assertThat(image.isDeleted()).isTrue();
        assertThat(image.getDeletedBy()).isEqualTo(ownerId);
    }

    @Test
    @DisplayName("존재하지 않는 이미지 삭제 시 404")
    void deleteImage_not_found_throws() {
        UUID imageId = UUID.randomUUID();
        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(storeImageRepository.findImageById(storeId, imageId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeImageService.deleteImage(storeId, imageId, ownerId, AuthConstants.OWNER))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.STORE_IMAGE_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 삭제된 이미지 삭제 시 400")
    void deleteImage_already_deleted_throws() {
        UUID imageId = UUID.randomUUID();
        StoreImage deleted = StoreImage.create(store, "url1", 1);
        deleted.delete(ownerId);

        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(storeImageRepository.findImageById(storeId, imageId)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> storeImageService.deleteImage(storeId, imageId, ownerId, AuthConstants.OWNER))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.STORE_IMAGE_ALREADY_DELETED);
    }

    @Test
    @DisplayName("OWNER가 타인 매장 이미지 삭제 시 403")
    void deleteImage_owner_cannot_delete_others() {
        UUID imageId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        // validate가 먼저 호출되므로 findImageById는 호출되지 않아야 함

        assertThatThrownBy(() -> storeImageService.deleteImage(storeId, imageId, otherId, AuthConstants.OWNER))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.STORE_IMAGE_ACCESS_DENIED);
    }

    // getImages

    @Test
    @DisplayName("이미지 목록 조회 - displayOrder 오름차순")
    void getImages_returns_sorted_list() {
        StoreImage img1 = StoreImage.create(store, "url1", 1);
        StoreImage img2 = StoreImage.create(store, "url2", 2);

        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        // Repository가 displayOrder 오름차순 정렬 후 반환하는 것을 mock으로 표현
        when(storeImageRepository.findAllImages(storeId)).thenReturn(List.of(img1, img2));

        List<StoreImageResult> results = storeImageService.getImages(storeId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getDisplayOrder()).isEqualTo(1);
        assertThat(results.get(1).getDisplayOrder()).isEqualTo(2);
    }

    // updateImage

    @Test
    @DisplayName("displayOrder 변경 시 목표 슬롯에 활성 이미지가 있으면 soft delete")
    void updateImage_evicts_active_slot_image() throws Exception {
        UUID imageId = UUID.randomUUID();
        StoreImage target = StoreImage.create(store, "url1", 1); // 변경 대상 이미지 (슬롯 1)
        StoreImage occupying = StoreImage.create(store, "url2", 2); // 슬롯 2에 있는 이미지
        setImageId(target, imageId); // JPA 없이 실행되므로 @GeneratedValue 미작동 → 수동 주입
        setImageId(occupying, UUID.randomUUID());

        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(storeImageRepository.findImageById(storeId, imageId)).thenReturn(Optional.of(target));
        when(storeImageRepository.findImageByDisplayOrder(storeId, 2)).thenReturn(Optional.of(occupying));
        doNothing().when(storeImageRepository).releaseImageSlot(any());

        UpdateStoreImageCommand command = UpdateStoreImageCommand.builder()
                .storeId(storeId)
                .imageId(imageId)
                .requesterId(ownerId)
                .imageUrl("url1-updated")
                .displayOrder(2) // 슬롯 2로 이동
                .build();

        storeImageService.updateImage(command, AuthConstants.OWNER);

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
        LocalDateTime deletedAtBefore = alreadyDeleted.getDeletedAt(); // delete() 재호출 여부 검증용

        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(storeImageRepository.findImageById(storeId, imageId)).thenReturn(Optional.of(target));
        when(storeImageRepository.findImageByDisplayOrder(storeId, 2)).thenReturn(Optional.of(alreadyDeleted));

        UpdateStoreImageCommand command = UpdateStoreImageCommand.builder()
                .storeId(storeId)
                .imageId(imageId)
                .requesterId(ownerId)
                .imageUrl("url1-updated")
                .displayOrder(2)
                .build();

        storeImageService.updateImage(command, AuthConstants.OWNER);

        assertThat(alreadyDeleted.isDeleted()).isTrue();
        assertThat(alreadyDeleted.getDeletedBy()).isEqualTo(ownerId);
        assertThat(alreadyDeleted.getDeletedAt()).isEqualTo(deletedAtBefore); // deletedAt 불변 = delete() 재호출 없음
    }

    @Test
    @DisplayName("OWNER가 타인 매장 이미지 수정 시 403 - 이미지 조회 전에 차단")
    void updateImage_owner_cannot_update_others() {
        UUID imageId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        // validate가 먼저 호출되므로 findImageById는 호출되지 않아야 함

        UpdateStoreImageCommand command = UpdateStoreImageCommand.builder()
                .storeId(storeId)
                .imageId(imageId)
                .requesterId(otherId)
                .imageUrl("url")
                .displayOrder(1)
                .build();

        assertThatThrownBy(() -> storeImageService.updateImage(command, AuthConstants.OWNER))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.STORE_IMAGE_ACCESS_DENIED);
    }

    @Test
    @DisplayName("이미 삭제된 이미지 수정 시 예외")
    void updateImage_on_deleted_image_throws() {
        UUID imageId = UUID.randomUUID();
        StoreImage deleted = StoreImage.create(store, "url1", 1);
        deleted.delete(ownerId);

        when(storeFinder.findActiveOrThrow(storeId)).thenReturn(store);
        when(storeImageRepository.findImageById(storeId, imageId)).thenReturn(Optional.of(deleted));

        UpdateStoreImageCommand command = UpdateStoreImageCommand.builder()
                .storeId(storeId)
                .imageId(imageId)
                .requesterId(ownerId)
                .imageUrl("new-url")
                .displayOrder(1)
                .build();

        assertThatThrownBy(() -> storeImageService.updateImage(command, AuthConstants.OWNER))
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
