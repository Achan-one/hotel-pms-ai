package com.hotel.service.report.dto;

import com.hotel.domain.ReservationStatus;
import com.hotel.domain.RoomType;

import java.time.LocalDate;

/**
 * 당일 도착 예정자(Arrivals List) 리포트 아이템 DTO
 */
public record ArrivalReportItemDto(
        String reservationId,
        String guestName,
        RoomType roomType,
        String roomTypeName,
        String assignedRoomNumber, // 미배정 시 "UNASSIGNED"
        LocalDate checkInDate,
        int stayNights,
        ReservationStatus status,
        boolean isAssigned,
        String specialRequestText,
        boolean isSettled // 선결제 또는 정산 완료 여부
) {}