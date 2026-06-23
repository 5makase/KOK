package com.omakase.kok.store.application;

import com.omakase.kok.store.application.cache.StoreListCacheRepository;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreCategory;
import com.omakase.kok.store.domain.enums.StoreStatus;
import com.omakase.kok.store.domain.repository.StoreRankingRepository;
import com.omakase.kok.store.domain.repository.StoreRepository;
import com.omakase.kok.store.domain.vo.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreRatingServiceTest {

    @Mock StoreRepository storeRepository;
    @Mock StoreRankingRepository storeRankingRepository;
    @Mock StoreListCacheRepository storeListCacheRepository;

    @InjectMocks
    StoreRatingService storeRatingService;

    private UUID storeId;
    private Store openStore;

    @BeforeEach
    void setUp() {
        storeId = UUID.randomUUID();
        StoreCategory category = StoreCategory.create("한식", 1, null);
        Store store = Store.create(UUID.randomUUID(), category, "테스트 매장", null,
                new Address("서울", "강남구", null, null, null, null), null, null);
        store.changeStatus(StoreStatus.OPEN, UUID.randomUUID());
        openStore = store;
    }

    @Test
    @DisplayName("OPEN 매장 평점 갱신 성공")
    void updateRating_open_store_success() {
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(openStore));
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        storeRatingService.updateRating(storeId, new BigDecimal("4.5"), 10);

        assertThat(openStore.getAverageRating()).isEqualByComparingTo("4.50");
        assertThat(openStore.getReviewCount()).isEqualTo(10);
        verify(storeRepository).save(openStore);
    }

    @Test
    @DisplayName("존재하지 않는 매장 - 평점 갱신 skip")
    void updateRating_store_not_found_skip() {
        when(storeRepository.findById(storeId)).thenReturn(Optional.empty());

        storeRatingService.updateRating(storeId, new BigDecimal("4.5"), 10);

        verify(storeRepository, never()).save(any());
        verify(storeRankingRepository, never()).updateScore(any(), any());
    }

    @Test
    @DisplayName("soft delete된 매장 - 평점 갱신 skip")
    void updateRating_deleted_store_skip() {
        StoreCategory category = StoreCategory.create("한식", 1, null);
        Store deletedStore = Store.create(UUID.randomUUID(), category, "삭제된 매장", null,
                new Address("서울", "강남구", null, null, null, null), null, null);
        deletedStore.delete(UUID.randomUUID());

        when(storeRepository.findById(storeId)).thenReturn(Optional.of(deletedStore));

        storeRatingService.updateRating(storeId, new BigDecimal("4.5"), 10);

        verify(storeRepository, never()).save(any());
        verify(storeRankingRepository, never()).updateScore(any(), any());
    }

    @Test
    @DisplayName("PERMANENTLY_CLOSED 매장 - 평점 갱신 skip")
    void updateRating_permanently_closed_store_skip() {
        StoreCategory category = StoreCategory.create("한식", 1, null);
        Store closedStore = Store.create(UUID.randomUUID(), category, "폐업 매장", null,
                new Address("서울", "강남구", null, null, null, null), null, null);
        closedStore.changeStatus(StoreStatus.OPEN, UUID.randomUUID());
        closedStore.changeStatus(StoreStatus.PERMANENTLY_CLOSED, UUID.randomUUID());

        when(storeRepository.findById(storeId)).thenReturn(Optional.of(closedStore));

        storeRatingService.updateRating(storeId, new BigDecimal("4.5"), 10);

        verify(storeRepository, never()).save(any());
        verify(storeRankingRepository, never()).updateScore(any(), any());
    }

    @Test
    @DisplayName("reviewCount == 0 - 랭킹에서 제거")
    void updateRating_review_count_zero_removes_ranking() {
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(openStore));
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        storeRatingService.updateRating(storeId, BigDecimal.ZERO, 0);

        verify(storeRankingRepository, never()).updateScore(any(), any());
        verify(storeRankingRepository).remove(storeId);
    }

    @Test
    @DisplayName("reviewCount > 0 - 랭킹 점수 갱신")
    void updateRating_review_count_positive_updates_score() {
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(openStore));
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        storeRatingService.updateRating(storeId, new BigDecimal("4.3"), 5);

        verify(storeRankingRepository, never()).remove(any());
        verify(storeRankingRepository).updateScore(storeId, new BigDecimal("4.30"));
    }
}
