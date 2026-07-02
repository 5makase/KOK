package com.omakase.kok.store.domain.entity;

import com.omakase.kok.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "p_store_hours",
        uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "day_of_week"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreHours extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "hours_id")
    private UUID hoursId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "day_of_week", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    @Column(name = "break_start_time")
    private LocalTime breakStartTime;

    @Column(name = "break_end_time")
    private LocalTime breakEndTime;

    @Column(name = "is_day_off", nullable = false)
    private boolean isDayOff;

    // 휴무일 등록
    public static StoreHours createDayOff(Store store, DayOfWeek dayOfWeek) {
        StoreHours hours = new StoreHours();
        hours.store = store;
        hours.dayOfWeek = dayOfWeek;
        hours.isDayOff = true;
        return hours;
    }

    // 영업일 등록 - openTime/closeTime 필수
    public static StoreHours createOperating(Store store, DayOfWeek dayOfWeek,
                                             LocalTime openTime, LocalTime closeTime,
                                             LocalTime breakStartTime, LocalTime breakEndTime) {
        StoreHours hours = new StoreHours();
        hours.store = store;
        hours.dayOfWeek = dayOfWeek;
        hours.isDayOff = false;
        hours.openTime = openTime;
        hours.closeTime = closeTime;
        hours.breakStartTime = breakStartTime;
        hours.breakEndTime = breakEndTime;
        return hours;
    }

    // 휴무일로 변경
    public void updateToDayOff() {
        this.isDayOff = true;
        this.openTime = null;
        this.closeTime = null;
        this.breakStartTime = null;
        this.breakEndTime = null;
    }

    // 영업일로 변경 - openTime/closeTime 필수
    public void updateToOperating(LocalTime openTime, LocalTime closeTime,
                                  LocalTime breakStartTime, LocalTime breakEndTime) {
        this.isDayOff = false;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.breakStartTime = breakStartTime;
        this.breakEndTime = breakEndTime;
    }

    // closeTime 영업종료시각 -> 정각 도달 시 마감으로 간주
    public boolean isOutsideBusinessHours(LocalTime time) {
        return time.isBefore(openTime) || !time.isBefore(closeTime);
    }

    // break_start_time / break_end_time - DB 스키마상 각각 독립적으로 NULL 허용
    public boolean isDuringBreakTime(LocalTime time) {
        return breakStartTime != null && breakEndTime != null
            && !time.isBefore(breakStartTime) && time.isBefore(breakEndTime);
    }

    // soft delete된 영업시간 재활성화 (UniqueConstraint 충돌 방지)
    public void restore() {
        clearDeleted();
    }
}
