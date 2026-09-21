package com.hotel.service.report.dto;

import com.hotel.domain.RoomType;

import java.time.LocalDate;

/**
 * 실제 재실 숙박자 명단 (In-House Guest List - 비상 대피/경찰 보고용)
 */
public record InHouseGuestDto(
        String roomNumber,
        int floor,
        String guestName,
        String reservationId,
        RoomType roomType,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int currentStayDay, // 몇 박째 투숙 중인지
        int totalNights
) {}