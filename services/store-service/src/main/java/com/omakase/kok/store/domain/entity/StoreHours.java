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

    // 영업시간 등록
    public static StoreHours create(Store store, DayOfWeek dayOfWeek, LocalTime openTime,
                                    LocalTime closeTime, LocalTime breakStartTime,
                                    LocalTime breakEndTime, boolean isDayOff) {
        StoreHours hours = new StoreHours();
        hours.store = store;
        hours.dayOfWeek = dayOfWeek;
        hours.openTime = openTime;
        hours.closeTime = closeTime;
        hours.breakStartTime = breakStartTime;
        hours.breakEndTime = breakEndTime;
        hours.isDayOff = isDayOff;
        return hours;
    }

    // 영업시간 수정 - isDayOff=true이면 시간 필드 null 강제 (휴무일에 영업시간 없음)
    public void update(LocalTime openTime, LocalTime closeTime,
                       LocalTime breakStartTime, LocalTime breakEndTime, boolean isDayOff) {
        this.isDayOff = isDayOff;
        if (isDayOff) {
            this.openTime = null;
            this.closeTime = null;
            this.breakStartTime = null;
            this.breakEndTime = null;
        } else {
            this.openTime = openTime;
            this.closeTime = closeTime;
            this.breakStartTime = breakStartTime;
            this.breakEndTime = breakEndTime;
        }
    }

    // soft delete된 영업시간 재활성화 (UniqueConstraint 충돌 방지)
    public void restore() {
        clearDeleted();
    }
}
