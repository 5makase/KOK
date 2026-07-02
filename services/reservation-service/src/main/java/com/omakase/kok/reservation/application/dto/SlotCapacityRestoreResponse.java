package com.omakase.kok.reservation.application.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SlotCapacityRestoreResponse {

    private UUID storeId;
    private LocalDate slotDate;
    private List<SlotCapacityRestoreItem> items;
    private LocalDateTime restoredAt;

    public static SlotCapacityRestoreResponse of(UUID storeId, LocalDate slotDate, List<SlotCapacityRestoreItem> items) {
        return new SlotCapacityRestoreResponse(storeId, slotDate, items, LocalDateTime.now());
    }

    public enum RestoreStatus {
        RESTORED,
        SKIPPED
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SlotCapacityRestoreItem {
        private UUID slotId;
        private RestoreStatus status;
        private Long before;
        private Long after;

        public static SlotCapacityRestoreItem restored(UUID slotId, long before, long after) {
            return new SlotCapacityRestoreItem(slotId, RestoreStatus.RESTORED, before, after);
        }

        public static SlotCapacityRestoreItem skipped(UUID slotId) {
            return new SlotCapacityRestoreItem(slotId, RestoreStatus.SKIPPED, null, null);
        }
    }
}
