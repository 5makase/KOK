package com.omakase.kok.reservation.domain.repository;

import com.omakase.kok.common.config.JpaConfig;
import com.omakase.kok.reservation.domain.entity.Reservation;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ReservationRepository 필터/페이지네이션 통합 테스트")
class ReservationRepositoryFilterIntegrationTest {

    @Autowired
    private ReservationRepository reservationRepository;

    private UUID userId;
    private UUID storeId;

    // userId+storeId 조합 3건, userId만 겹치는 1건(다른 매장), storeId만 겹치는 1건(다른 사용자)
    // findMyReservations(userId)  → r1,r2,r3,r4 (4건, 매장 무관)
    // findStoreReservations(storeId) → r1,r2,r3,r5 (4건, 사용자 무관)
    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        storeId = UUID.randomUUID();

        save(userId, storeId, ReservationStatus.CONFIRMED, LocalDateTime.now().plusDays(1));   // r1
        save(userId, storeId, ReservationStatus.CANCELLED, LocalDateTime.now().plusDays(2));   // r2
        save(userId, storeId, ReservationStatus.CONFIRMED, LocalDateTime.now().plusDays(10));  // r3
        save(userId, UUID.randomUUID(), ReservationStatus.CONFIRMED, LocalDateTime.now().plusDays(1)); // r4: 같은 사용자, 다른 매장
        save(UUID.randomUUID(), storeId, ReservationStatus.CONFIRMED, LocalDateTime.now().plusDays(1)); // r5: 다른 사용자, 같은 매장
    }

    private void save(UUID userId, UUID storeId, ReservationStatus status, LocalDateTime scheduledAt) {
        Reservation r = Reservation.builder()
                .slotId(UUID.randomUUID())
                .userId(userId)
                .storeId(storeId)
                .storeName("테스트 매장")
                .scheduledAt(scheduledAt)
                .bookerName("홍길동")
                .bookerPhone("010-1234-5678")
                .reservationSize(2)
                .build();
        ReflectionTestUtils.setField(r, "status", status);
        reservationRepository.save(r);
    }

    @Test
    @DisplayName("status/from/to가 모두 null이면 해당 사용자의 전체 예약을 매장과 무관하게 반환한다")
    void findMyReservations_noFilter_returnsAll() {
        Page<Reservation> result = reservationRepository.findMyReservations(
                userId, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(4); // r1,r2,r3,r4
    }

    @Test
    @DisplayName("status로 필터링하면 해당 상태의 예약만 반환한다")
    void findMyReservations_filterByStatus() {
        Page<Reservation> result = reservationRepository.findMyReservations(
                userId, ReservationStatus.CONFIRMED, null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(3); // r1,r3,r4 (r2는 CANCELLED)
        assertThat(result.getContent())
                .allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(ReservationStatus.CONFIRMED));
    }

    @Test
    @DisplayName("from/to 범위로 필터링하면 해당 기간 내 예약만 반환한다")
    void findMyReservations_filterByDateRange() {
        Page<Reservation> result = reservationRepository.findMyReservations(
                userId, null,
                LocalDateTime.now(), LocalDateTime.now().plusDays(3),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(3); // r1,r2,r4 (r3는 +10일이라 범위 밖)
    }

    @Test
    @DisplayName("페이지 크기만큼 잘라서 반환하고 totalElements는 전체 개수를 유지한다")
    void findMyReservations_pagination() {
        Page<Reservation> result = reservationRepository.findMyReservations(
                userId, null, null, null, PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("storeId/status/date로 매장 예약을 필터링한다")
    void findStoreReservations_filterByStatusAndDate() {
        LocalDateTime dateStart = LocalDateTime.now().toLocalDate().plusDays(1).atStartOfDay();
        LocalDateTime dateEnd = dateStart.plusDays(1);

        Page<Reservation> result = reservationRepository.findStoreReservations(
                storeId, ReservationStatus.CONFIRMED, dateStart, dateEnd, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2); // r1,r5 (r2는 CANCELLED, r3는 날짜 밖)
        assertThat(result.getContent())
                .allSatisfy(r -> assertThat(r.getStoreId()).isEqualTo(storeId));
    }

    @Test
    @DisplayName("date가 null이면 해당 매장의 모든 날짜의 예약을 반환한다")
    void findStoreReservations_noDateFilter_returnsAllDates() {
        Page<Reservation> result = reservationRepository.findStoreReservations(
                storeId, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(4); // r1,r2,r3,r5
    }
}
