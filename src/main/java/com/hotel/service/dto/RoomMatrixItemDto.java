package com.hotel.service.dto;

import com.hotel.domain.RoomStatus;
import com.hotel.domain.RoomType;

public record RoomMatrixItemDto(
        String roomNumber,
        int floor,
        RoomType roomType,
        String roomTypeName,
        boolean nearElevator,
        boolean cornerRoom,
        RoomStatus status,
        String reservationId,     // 미배정/공실 시 null
        String guestName,         // 미배정/공실 시 null
        String stayPeriodStr      // 예: "2026-09-20 ~ 2026-09-23"
) {
}