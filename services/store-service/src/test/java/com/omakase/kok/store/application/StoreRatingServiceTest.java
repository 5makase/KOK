package com.omakase.kok.store.application;

import com.omakase.kok.store.application.cache.StoreListCacheRepository;
import com.omakase.kok.store.application.result.StoreRankingResult;
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

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
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

    // getRanking

    @Test
    @DisplayName("랭킹 조회 - Redis 순서대로 결과 반환")
    void getRanking_returns_in_redis_order() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Store store1 = makeOpenStore("1위 매장", id1);
        Store store2 = makeOpenStore("2위 매장", id2);

        when(storeRankingRepository.getTopRanking(5)).thenReturn(List.of(id1, id2));
        // DB 반환 순서와 무관하게 Redis 순서(id1→id2)대로 정렬되는지 검증
        when(storeRepository.findActiveStoresByIds(List.of(id1, id2))).thenReturn(List.of(store2, store1));

        List<StoreRankingResult> results = storeRatingService.getRanking(5);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getRank()).isEqualTo(1);
        assertThat(results.get(0).getName()).isEqualTo("1위 매장");
        assertThat(results.get(1).getRank()).isEqualTo(2);
    }

    @Test
    @DisplayName("랭킹 데이터 없으면 빈 리스트 반환")
    void getRanking_empty_when_no_data() {
        when(storeRankingRepository.getTopRanking(5)).thenReturn(List.of());

        List<StoreRankingResult> results = storeRatingService.getRanking(5);

        assertThat(results).isEmpty();
        verify(storeRepository, never()).findActiveStoresByIds(any());
    }

    @Test
    @DisplayName("size=0 입력 시 1로 보정하여 조회")
    void getRanking_size_zero_clamped_to_one() throws Exception {
        UUID id1 = UUID.randomUUID();
        Store store1 = makeOpenStore("매장", id1);

        when(storeRankingRepository.getTopRanking(1)).thenReturn(List.of(id1));
        when(storeRepository.findActiveStoresByIds(List.of(id1))).thenReturn(List.of(store1));

        List<StoreRankingResult> results = storeRatingService.getRanking(0);

        assertThat(results).hasSize(1);
        verify(storeRankingRepository).getTopRanking(1);
    }

    @Test
    @DisplayName("size=100 입력 시 50으로 보정하여 조회")
    void getRanking_size_over_max_clamped_to_fifty() {
        when(storeRankingRepository.getTopRanking(50)).thenReturn(List.of());

        storeRatingService.getRanking(100);

        verify(storeRankingRepository).getTopRanking(50);
    }

    @Test
    @DisplayName("Redis에 있지만 DB에 없는 매장은 결과에서 제외")
    void getRanking_excludes_store_missing_in_db() throws Exception {
        UUID activeId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID(); // DB에서 soft delete된 매장
        Store activeStore = makeOpenStore("활성 매장", activeId);

        when(storeRankingRepository.getTopRanking(5)).thenReturn(List.of(activeId, deletedId));
        when(storeRepository.findActiveStoresByIds(List.of(activeId, deletedId)))
                .thenReturn(List.of(activeStore)); // deletedId는 반환되지 않음

        List<StoreRankingResult> results = storeRatingService.getRanking(5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRank()).isEqualTo(1);
    }

    // updateRating

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

    // helpers

    private Store makeOpenStore(String name) {
        StoreCategory category = StoreCategory.create("한식", 1, null);
        Store store = Store.create(UUID.randomUUID(), category, name, null,
                new Address("서울", "강남구", null, null, null, null), null, null);
        store.changeStatus(StoreStatus.OPEN, UUID.randomUUID());
        return store;
    }

    // @GeneratedValue는 JPA 영속 시점에만 동작하므로 단위 테스트에서는 리플렉션으로 주입
    private Store makeOpenStore(String name, UUID id) throws Exception {
        Store store = makeOpenStore(name);
        Field field = Store.class.getDeclaredField("storeId");
        field.setAccessible(true);
        field.set(store, id);
        return store;
    }
}
