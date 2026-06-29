package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreCategory;
import com.omakase.kok.store.domain.enums.StoreStatus;
import com.omakase.kok.store.domain.repository.StoreSearchCondition;
import com.omakase.kok.store.domain.repository.StoreSearchCondition.SortType;
import com.omakase.kok.store.domain.vo.Address;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Haversine DISTANCE 정렬 통합 테스트 - PostgreSQL 전용
 * 실행 전제: docker compose up -d postgres
 */
@Disabled("로컬 전용 통합 테스트 - PostgreSQL 필요(Haversine), CI 환경 미구성으로 skip (로컬에서 수동 실행)")
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/kok_db",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=create"
})
@Transactional
class StoreQueryRepositoryDistanceIntegrationTest {

    @Autowired EntityManager em;
    @Autowired StoreQueryRepository repository;

    // 강남역 인근 (서울)
    static final BigDecimal GANGNAM_LAT = new BigDecimal("37.4979");
    static final BigDecimal GANGNAM_LNG = new BigDecimal("127.0276");

    // 판교역 인근 (강남역에서 약 12km)
    static final BigDecimal PANGYO_LAT = new BigDecimal("37.3947");
    static final BigDecimal PANGYO_LNG = new BigDecimal("127.1112");

    private StoreCategory category;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        category = StoreCategory.create("일식", 1, null);
        em.persist(category);
        ownerId = UUID.randomUUID();
    }

    @Test
    @DisplayName("DISTANCE 정렬 - 기준 좌표에 가까운 순으로 정렬")
    void search_sort_by_distance() {
        Store nearby = openStore("가까운가게", "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG);
        Store far = openStore("먼가게", "경기도", "성남시", PANGYO_LAT, PANGYO_LNG);
        em.persist(nearby);
        em.persist(far);
        em.flush();

        Page<Store> result = repository.search(
                StoreSearchCondition.builder()
                        .sort(SortType.DISTANCE)
                        .latitude(GANGNAM_LAT)
                        .longitude(GANGNAM_LNG)
                        .build(),
                PageRequest.of(0, 10));

        List<String> names = result.getContent().stream().map(Store::getName).toList();
        assertThat(names.get(0)).isEqualTo("가까운가게");
        assertThat(names.get(1)).isEqualTo("먼가게");
    }

    @Test
    @DisplayName("withinRadius + DISTANCE 정렬 - 반경 내 가까운 순")
    void search_within_radius_sorted_by_distance() {
        Store nearby = openStore("근처가게", "서울", "강남구", GANGNAM_LAT, GANGNAM_LNG);
        Store far = openStore("먼가게", "경기도", "성남시", PANGYO_LAT, PANGYO_LNG);
        em.persist(nearby);
        em.persist(far);
        em.flush();

        Page<Store> result = repository.search(
                StoreSearchCondition.builder()
                        .latitude(GANGNAM_LAT)
                        .longitude(GANGNAM_LNG)
                        .radiusKm(5.0)
                        .sort(SortType.DISTANCE)
                        .build(),
                PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("근처가게");
    }

    private Store openStore(String name, String sido, String sigungu,
                             BigDecimal lat, BigDecimal lng) {
        Store store = Store.create(ownerId, category, name, null,
                new Address(sido, sigungu, null, null, lat, lng), null, 50);
        store.changeStatus(StoreStatus.OPEN, ownerId);
        return store;
    }
}
