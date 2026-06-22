package com.omakase.kok.reservation.application.dto;

import com.omakase.kok.reservation.domain.entity.Reservation;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ReservationResponse {

    private UUID reservationId;
    private UUID slotId;
    private UUID userId;
    private UUID storeId;
    private String bookerName;
    private String bookerPhone;
    private int reservationSize;
    private String requestMessage;
    private ReservationStatus status;
    private LocalDateTime createdAt;

    public static ReservationResponse from(Reservation reservation) {
        return ReservationResponse.builder()
                .reservationId(reservation.getReservationId())
                .slotId(reservation.getSlotId())
                .userId(reservation.getUserId())
                .storeId(reservation.getStoreId())
                .bookerName(reservation.getBookerName())
                .bookerPhone(reservation.getBookerPhone())
                .reservationSize(reservation.getReservationSize())
                .requestMessage(reservation.getRequestMessage())
                .status(reservation.getStatus())
                .createdAt(reservation.getCreatedAt())
                .build();
    }
}
