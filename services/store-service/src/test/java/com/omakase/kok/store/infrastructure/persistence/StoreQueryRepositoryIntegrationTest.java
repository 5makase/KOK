package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreAmenity;
import com.omakase.kok.store.domain.entity.StoreCategory;
import com.omakase.kok.store.domain.enums.AmenityType;
import com.omakase.kok.store.domain.enums.StoreStatus;
import com.omakase.kok.store.domain.repository.StoreSearchCondition;
import com.omakase.kok.store.domain.repository.StoreSearchCondition.SortType;
import com.omakase.kok.store.domain.vo.Address;
import com.omakase.kok.store.global.config.QueryDslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StoreQueryRepository 통합 테스트 - H2 인메모리 DB 사용
 * @AutoConfigureTestDatabase(replace = NONE): application-test.yml의 H2 datasource 사용
 * DISTANCE 정렬 + withinRadius 복합 테스트는 PostgreSQL 전용 파일(StoreQueryRepositoryDistanceIntegrationTest) 참고
 */
@DataJpaTest
@Import(QueryDslConfig.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
class StoreQueryRepositoryIntegrationTest {

    @Autowired EntityManager em;
    StoreQueryRepository repository;

    // 강남역 인근 좌표 (서울)
    static final BigDecimal GANGNAM_LAT = new BigDecimal("37.4979");
    static final BigDecimal GANGNAM_LNG = new BigDecimal("127.0276");

    // 판교역 인근 좌표 (경기도 - 강남역에서 약 12km)
    static final BigDecimal PANGYO_LAT = new BigDecimal("37.3947");
    static final BigDecimal PANGYO_LNG = new BigDecimal("127.1112");

    private StoreCategory category;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        repository = new StoreQueryRepository(
                new com.querydsl.jpa.impl.JPAQueryFactory(em));

        category = StoreCategory.create("일식", 1, null);
        em.persist(category);

        ownerId = UUID.randomUUID();
    }

    // 기본 필터

    @Test
    @DisplayName("sido 필터 - 해당 시도 매장만 조회")
    void search_filters_by_sido() {
        persist(openStore("서울가게", "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG));
        persist(openStore("부산가게", "부산", "해운대구", GANGNAM_LAT, GANGNAM_LNG));

        Page<Store> result = repository.search(
                StoreSearchCondition.builder().sido("서울").build(),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("서울가게");
    }

    @Test
    @DisplayName("sigungu 필터 - 해당 시군구 매장만 조회")
    void search_filters_by_sigungu() {
        persist(openStore("강남가게", "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG));
        persist(openStore("서초가게", "서울", "서초구", GANGNAM_LAT, GANGNAM_LNG));

        Page<Store> result = repository.search(
                StoreSearchCondition.builder().sido("서울").sigungu("강남구").build(),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("강남가게");
    }

    @Test
    @DisplayName("keyword 필터 - 이름 포함 매장 조회 (대소문자 무관)")
    void search_filters_by_keyword_case_insensitive() {
        persist(openStore("스시 오마카세", "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG));
        persist(openStore("한우 숯불구이", "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG));

        Page<Store> result = repository.search(
                StoreSearchCondition.builder().keyword("오마카세").build(),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).contains("오마카세");
    }

    @Test
    @DisplayName("status 필터 - OPEN 매장만 조회")
    void search_filters_by_status_open() {
        Store open = openStore("오픈가게", "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG);
        // PREPARING(기본값) 상태 매장 - changeStatus 없이 create만
        Store prep = Store.create(ownerId, category, "준비중가게", null,
                new Address("서울", "강남구", null, null, GANGNAM_LAT, GANGNAM_LNG), null, 50);
        persist(open);
        persist(prep);

        Page<Store> result = repository.search(
                StoreSearchCondition.builder().status(StoreStatus.OPEN).build(),
                PageRequest.of(0, 10));

        assertThat(result.getContent()).allMatch(s -> s.getStatus() == StoreStatus.OPEN);
        assertThat(result.getContent().stream().map(Store::getName))
                .contains("오픈가게")
                .doesNotContain("준비중가게");
    }

    @Test
    @DisplayName("soft delete - deletedAt 설정된 매장은 조회 안 됨")
    void search_excludes_deleted_stores() {
        Store deleted = openStore("삭제된가게", "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG);
        persist(deleted);
        // PERMANENTLY_CLOSED 전이 시 soft delete(deletedAt) 자동 설정
        deleted.changeStatus(StoreStatus.PERMANENTLY_CLOSED, ownerId);
        em.flush();

        Page<Store> result = repository.search(
                StoreSearchCondition.builder().sido("서울").build(),
                PageRequest.of(0, 10));

        assertThat(result.getContent()).noneMatch(s -> s.getName().equals("삭제된가게"));
    }

    // 카테고리 필터 (inCategories)

    @Test
    @DisplayName("categoryIds null → 카테고리 필터 없음, 전체 조회")
    void search_null_categoryIds_returns_all() {
        StoreCategory other = StoreCategory.create("중식", 2, null);
        em.persist(other);

        persist(openStore("일식가게", "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG));
        Store otherStore = Store.create(ownerId, other, "중식가게", null,
                new Address("서울", "강남구", null, null, GANGNAM_LAT, GANGNAM_LNG), null, 50);
        otherStore.changeStatus(StoreStatus.OPEN, ownerId);
        persist(otherStore);

        Page<Store> result = repository.search(
                StoreSearchCondition.builder().build(),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("categoryIds 빈 리스트 → Expressions.FALSE → 결과 없음")
    void search_empty_categoryIds_returns_nothing() {
        persist(openStore("일식가게", "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG));

        Page<Store> result = repository.search(
                StoreSearchCondition.builder().categoryIds(List.of()).build(),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("categoryIds 특정 ID → 해당 카테고리 매장만 조회")
    void search_filters_by_categoryIds() {
        StoreCategory other = StoreCategory.create("중식", 2, null);
        em.persist(other);

        persist(openStore("일식가게", "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG));
        Store otherStore = Store.create(ownerId, other, "중식가게", null,
                new Address("서울", "강남구", null, null, GANGNAM_LAT, GANGNAM_LNG), null, 50);
        otherStore.changeStatus(StoreStatus.OPEN, ownerId);
        persist(otherStore);

        Page<Store> result = repository.search(
                StoreSearchCondition.builder()
                        .categoryIds(List.of(category.getCategoryId()))
                        .build(),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("일식가게");
    }

    // 편의시설 필터

    @Test
    @DisplayName("amenity 필터 - WIFI 있는 매장만 조회")
    void search_filters_by_amenity() {
        Store withWifi = openStore("WIFI있는가게", "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG);
        Store noWifi = openStore("WIFI없는가게", "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG);
        persist(withWifi);
        persist(noWifi);

        StoreAmenity wifi = StoreAmenity.create(withWifi, AmenityType.WIFI);
        em.persist(wifi);
        em.flush();

        Page<Store> result = repository.search(
                StoreSearchCondition.builder().amenities(List.of(AmenityType.WIFI)).build(),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("WIFI있는가게");
    }

    // Haversine 반경 필터

    @Test
    @DisplayName("withinRadius - 반경 내 매장만 조회")
    void search_within_radius_includes_close_store() {
        // 강남역에서 약 0.5km 내에 있는 매장 (같은 좌표)
        Store nearby = openStore("근처가게", "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG);
        // 판교 - 강남역에서 약 12km
        Store far = openStore("먼가게", "경기도", "성남시", PANGYO_LAT, PANGYO_LNG);
        persist(nearby);
        persist(far);

        Page<Store> result = repository.search(
                StoreSearchCondition.builder()
                        .latitude(GANGNAM_LAT)
                        .longitude(GANGNAM_LNG)
                        .radiusKm(5.0)
                        .build(),
                PageRequest.of(0, 10));

        assertThat(result.getContent().stream().map(Store::getName))
                .contains("근처가게")
                .doesNotContain("먼가게");
    }

    @Test
    @DisplayName("withinRadius - lat/lng/radiusKm 중 하나라도 null이면 반경 필터 비활성화")
    void search_without_radius_returns_all() {
        persist(openStore("서울가게", "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG));
        persist(openStore("판교가게", "경기도", "성남시", PANGYO_LAT, PANGYO_LNG));

        // radiusKm 없이 좌표만 전달 - 반경 필터 미작동
        Page<Store> result = repository.search(
                StoreSearchCondition.builder()
                        .latitude(GANGNAM_LAT)
                        .longitude(GANGNAM_LNG)
                        // radiusKm null
                        .build(),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    // 정렬

    @Test
    @DisplayName("RATING 정렬 - 평점 높은 순")
    void search_sort_by_rating() throws Exception {
        Store low = openStore("낮은평점", "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG);
        Store high = openStore("높은평점", "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG);
        persist(low);
        persist(high);

        // reflection으로 averageRating 주입
        setField(low, "averageRating", new BigDecimal("3.00"));
        setField(high, "averageRating", new BigDecimal("4.80"));
        em.flush();

        Page<Store> result = repository.search(
                StoreSearchCondition.builder().sido("서울").sort(SortType.RATING).build(),
                PageRequest.of(0, 10));

        List<String> names = result.getContent().stream().map(Store::getName).toList();
        assertThat(names.indexOf("높은평점")).isLessThan(names.indexOf("낮은평점"));
    }

    @Test
    @DisplayName("DISTANCE 정렬, 좌표 없으면 createdAt 내림차순으로 fallback")
    void search_sort_distance_no_coordinate_falls_back_to_created_at() {
        persist(openStore("1번가게", "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG));
        persist(openStore("2번가게", "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG));

        // 예외 없이 정상 동작해야 함
        Page<Store> result = repository.search(
                StoreSearchCondition.builder().sort(SortType.DISTANCE).build(),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    // 페이지네이션

    @Test
    @DisplayName("페이지네이션 - offset/limit 동작")
    void search_pagination() {
        for (int i = 1; i <= 5; i++) {
            persist(openStore("가게" + i, "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG));
        }

        Page<Store> page0 = repository.search(
                StoreSearchCondition.builder().sido("서울").build(),
                PageRequest.of(0, 3));
        Page<Store> page1 = repository.search(
                StoreSearchCondition.builder().sido("서울").build(),
                PageRequest.of(1, 3));

        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getContent()).hasSize(3);
        assertThat(page1.getContent()).hasSize(2);
    }

    // 헬퍼 메서드

    private Store openStore(String name, String sido, String sigungu,
                             BigDecimal lat, BigDecimal lng) {
        Store store = Store.create(ownerId, category, name, null,
                new Address(sido, sigungu, null, null, lat, lng), null, 50);
        // PREPARING → OPEN
        store.changeStatus(StoreStatus.OPEN, ownerId);
        return store;
    }

    private void persist(Store store) {
        em.persist(store);
        em.flush();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
