package com.omakase.kok.reservation.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.reservation.application.dto.ReservationStatisticsResponse;
import com.omakase.kok.reservation.domain.entity.ReservationSlot;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import com.omakase.kok.reservation.domain.exception.ReservationErrorCode;
import com.omakase.kok.reservation.domain.repository.ReservationRepository;
import com.omakase.kok.reservation.domain.repository.ReservationRepository.ReservationStatsRow;
import com.omakase.kok.reservation.domain.repository.ReservationSlotRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationStatisticsService {

    private static final String CACHE_KEY_PREFIX = "stats:store:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final int MAX_PERIOD_MONTHS = 3;
    private static final Set<ReservationStatus> COUNTED_STATUSES =
            EnumSet.of(ReservationStatus.CONFIRMED, ReservationStatus.VISITED,
                    ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW);
    private static final Set<ReservationStatus> DEPOSIT_STATUSES =
            EnumSet.of(ReservationStatus.CONFIRMED, ReservationStatus.VISITED);

    private final ReservationRepository reservationRepository;
    private final ReservationSlotRepository slotRepository;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    public ReservationStatisticsResponse getStatistics(UUID storeId, LocalDate from, LocalDate to) {
        validatePeriod(from, to);

        String cacheKey = CACHE_KEY_PREFIX + storeId + ":" + from + ":" + to;
        RBucket<String> bucket = redissonClient.getBucket(cacheKey);
        String cached = bucket.get();
        if (cached != null) {
            return deserialize(cached);
        }

        ReservationStatisticsResponse response = calculate(storeId, from, to);
        bucket.set(serialize(response), CACHE_TTL);
        return response;
    }

    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || from.plusMonths(MAX_PERIOD_MONTHS).isBefore(to)) {
            throw new BaseException(ReservationErrorCode.STATISTICS_PERIOD_INVALID);
        }
    }

    private ReservationStatisticsResponse calculate(UUID storeId, LocalDate from, LocalDate to) {
        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();

        List<ReservationStatsRow> rows = reservationRepository
                .findStatsRowsByStoreIdAndScheduledAtBetween(storeId, fromDateTime, toDateTime, COUNTED_STATUSES);

        Map<UUID, Long> depositBySlotId = loadDepositAmounts(rows);

        long confirmed = countByStatus(rows, ReservationStatus.CONFIRMED, ReservationStatus.VISITED);
        long cancelled = countByStatus(rows, ReservationStatus.CANCELLED);
        long noShow = countByStatus(rows, ReservationStatus.NO_SHOW);
        long total = confirmed + cancelled + noShow;
        long totalDepositAmount = sumDeposit(rows, depositBySlotId);

        Map<LocalDate, List<ReservationStatsRow>> byDate = rows.stream()
                .collect(Collectors.groupingBy(row -> row.getScheduledAt().toLocalDate(), TreeMap::new, Collectors.toList()));

        List<ReservationStatisticsResponse.DailyStat> dailyStats = byDate.entrySet().stream()
                .map(entry -> new ReservationStatisticsResponse.DailyStat(
                        entry.getKey(),
                        entry.getValue().size(),
                        sumDeposit(entry.getValue(), depositBySlotId)))
                .toList();

        return new ReservationStatisticsResponse(
                new ReservationStatisticsResponse.Period(from, to),
                total,
                confirmed,
                cancelled,
                noShow,
                rate(cancelled, total),
                rate(noShow, total),
                totalDepositAmount,
                dailyStats
        );
    }

    private Map<UUID, Long> loadDepositAmounts(List<ReservationStatsRow> rows) {
        List<UUID> slotIds = rows.stream()
                .filter(row -> DEPOSIT_STATUSES.contains(row.getStatus()))
                .map(ReservationStatsRow::getSlotId)
                .distinct()
                .toList();
        if (slotIds.isEmpty()) {
            return Map.of();
        }
        return slotRepository.findAllById(slotIds).stream()
                .collect(Collectors.toMap(ReservationSlot::getSlotId,
                        slot -> slot.getDepositAmount() != null ? slot.getDepositAmount() : 0L));
    }

    private long countByStatus(List<ReservationStatsRow> rows, ReservationStatus... statuses) {
        Set<ReservationStatus> target = Set.of(statuses);
        return rows.stream().filter(row -> target.contains(row.getStatus())).count();
    }

    private long sumDeposit(List<ReservationStatsRow> rows, Map<UUID, Long> depositBySlotId) {
        return rows.stream()
                .filter(row -> DEPOSIT_STATUSES.contains(row.getStatus()))
                .mapToLong(row -> depositBySlotId.getOrDefault(row.getSlotId(), 0L))
                .sum();
    }

    private double rate(long part, long total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round(part * 1000.0 / total) / 10.0;
    }

    private String serialize(ReservationStatisticsResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new BaseException(ReservationErrorCode.PAYLOAD_SERIALIZATION_FAILED);
        }
    }

    private ReservationStatisticsResponse deserialize(String cached) {
        try {
            return objectMapper.readValue(cached, ReservationStatisticsResponse.class);
        } catch (JsonProcessingException e) {
            throw new BaseException(ReservationErrorCode.PAYLOAD_SERIALIZATION_FAILED);
        }
    }
}
