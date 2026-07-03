package com.omakase.kok.reservation.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.reservation.application.dto.ReservationStatisticsResponse;
import com.omakase.kok.reservation.domain.entity.ReservationSlot;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import com.omakase.kok.reservation.domain.exception.ReservationErrorCode;
import com.omakase.kok.reservation.domain.repository.ReservationRepository;
import com.omakase.kok.reservation.domain.repository.ReservationRepository.ReservationStatsRow;
import com.omakase.kok.reservation.domain.repository.ReservationSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationStatisticsService 단위 테스트")
class ReservationStatisticsServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationSlotRepository slotRepository;
    @Mock private RedissonClient redissonClient;
    @Mock private RBucket<String> bucket;

    private ObjectMapper objectMapper;
    private ReservationStatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        statisticsService = new ReservationStatisticsService(
                reservationRepository, slotRepository, redissonClient, objectMapper);
    }

    private ReservationStatsRow row(UUID slotId, ReservationStatus status, LocalDateTime scheduledAt) {
        ReservationStatsRow row = mock(ReservationStatsRow.class);
        lenient().when(row.getSlotId()).thenReturn(slotId);
        lenient().when(row.getStatus()).thenReturn(status);
        lenient().when(row.getScheduledAt()).thenReturn(scheduledAt);
        return row;
    }

    private ReservationSlot slotWithDeposit(UUID slotId, Long depositAmount) {
        ReservationSlot slot = ReservationSlot.builder()
                .storeId(UUID.randomUUID())
                .slotDate(LocalDate.now().plusDays(5))
                .slotTime(LocalTime.of(18, 0))
                .maxCapacity(4)
                .depositRequired(depositAmount != null)
                .depositAmount(depositAmount)
                .build();
        ReflectionTestUtils.setField(slot, "slotId", slotId);
        return slot;
    }

    @Test
    @DisplayName("상태별 건수, 취소율/노쇼율, 예약금 합계를 정확히 집계한다")
    void calculatesStatisticsCorrectly() {
        UUID storeId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        UUID slotA = UUID.randomUUID();
        UUID slotB = UUID.randomUUID();
        UUID slotC = UUID.randomUUID();

        List<ReservationStatsRow> rows = List.of(
                row(slotA, ReservationStatus.CONFIRMED, LocalDateTime.of(2026, 6, 1, 18, 0)),
                row(slotB, ReservationStatus.VISITED, LocalDateTime.of(2026, 6, 1, 19, 0)),
                row(slotC, ReservationStatus.CANCELLED, LocalDateTime.of(2026, 6, 2, 18, 0)),
                row(slotA, ReservationStatus.NO_SHOW, LocalDateTime.of(2026, 6, 2, 19, 0))
        );

        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.get()).thenReturn(null);
        when(reservationRepository.findStatsRowsByStoreIdAndScheduledAtBetween(eq(storeId), any(), any(), any()))
                .thenReturn(rows);
        when(slotRepository.findAllById(any()))
                .thenReturn(List.of(slotWithDeposit(slotA, 10000L), slotWithDeposit(slotB, 20000L)));

        ReservationStatisticsResponse response = statisticsService.getStatistics(storeId, from, to);

        assertThat(response.totalReservations()).isEqualTo(4);
        assertThat(response.confirmed()).isEqualTo(2);
        assertThat(response.cancelled()).isEqualTo(1);
        assertThat(response.noShow()).isEqualTo(1);
        assertThat(response.cancellationRate()).isEqualTo(25.0);
        assertThat(response.noShowRate()).isEqualTo(25.0);
        assertThat(response.totalDepositAmount()).isEqualTo(30000L);
        assertThat(response.dailyStats()).hasSize(2);
        assertThat(response.dailyStats().get(0).date()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(response.dailyStats().get(0).count()).isEqualTo(2);
        assertThat(response.dailyStats().get(0).revenue()).isEqualTo(30000L);
        assertThat(response.dailyStats().get(1).date()).isEqualTo(LocalDate.of(2026, 6, 2));
        assertThat(response.dailyStats().get(1).count()).isEqualTo(2);
        assertThat(response.dailyStats().get(1).revenue()).isEqualTo(0L);

        verify(bucket).set(anyString(), eq(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("기간 내 예약이 0건이면 비율은 NaN이 아닌 0.0을 반환한다")
    void returnsZeroRatesWhenNoReservations() {
        UUID storeId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.get()).thenReturn(null);
        when(reservationRepository.findStatsRowsByStoreIdAndScheduledAtBetween(eq(storeId), any(), any(), any()))
                .thenReturn(List.of());

        ReservationStatisticsResponse response = statisticsService.getStatistics(storeId, from, to);

        assertThat(response.totalReservations()).isZero();
        assertThat(response.cancellationRate()).isEqualTo(0.0);
        assertThat(response.noShowRate()).isEqualTo(0.0);
        assertThat(response.totalDepositAmount()).isZero();
        assertThat(response.dailyStats()).isEmpty();
        verify(slotRepository, never()).findAllById(any());
        verify(bucket).set(anyString(), eq(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("캐시에 값이 있으면 리포지토리를 조회하지 않고 캐시된 값을 반환한다")
    void returnsCachedResponseWithoutQuerying() throws Exception {
        UUID storeId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        ReservationStatisticsResponse cachedResponse = new ReservationStatisticsResponse(
                new ReservationStatisticsResponse.Period(from, to),
                10, 8, 1, 1, 10.0, 10.0, 50000L, List.of());
        String cachedJson = objectMapper.writeValueAsString(cachedResponse);

        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.get()).thenReturn(cachedJson);

        ReservationStatisticsResponse response = statisticsService.getStatistics(storeId, from, to);

        assertThat(response).isEqualTo(cachedResponse);
        verify(reservationRepository, never())
                .findStatsRowsByStoreIdAndScheduledAtBetween(any(), any(), any(), any());
        verify(bucket, never()).set(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("from이 to보다 미래이면 STATISTICS_PERIOD_INVALID 예외가 발생한다")
    void throwsWhenFromAfterTo() {
        UUID storeId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 6, 30);
        LocalDate to = LocalDate.of(2026, 6, 1);

        assertThatThrownBy(() -> statisticsService.getStatistics(storeId, from, to))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ReservationErrorCode.STATISTICS_PERIOD_INVALID);
    }

    @Test
    @DisplayName("기간이 3개월을 초과하면 STATISTICS_PERIOD_INVALID 예외가 발생한다")
    void throwsWhenPeriodExceedsThreeMonths() {
        UUID storeId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 4, 2);

        assertThatThrownBy(() -> statisticsService.getStatistics(storeId, from, to))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ReservationErrorCode.STATISTICS_PERIOD_INVALID);
    }
}
