package com.omakase.kok.reservation.application.dto;

import java.time.LocalDate;
import java.util.List;

public record ReservationStatisticsResponse(
        Period period,
        long totalReservations,
        long confirmed,
        long cancelled,
        long noShow,
        double cancellationRate,
        double noShowRate,
        long totalDepositAmount,
        List<DailyStat> dailyStats
) {
    public record Period(LocalDate from, LocalDate to) {
    }

    public record DailyStat(LocalDate date, long count, long revenue) {
    }
}
