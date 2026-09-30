package com.hotel.service.report.dto;

import com.hotel.domain.ReservationStatus;
import com.hotel.domain.RoomType;

import java.time.LocalDate;

/**
 * 숙박자 명단의 한 행. 조회 기간 안에 하룻밤이라도 묵는 예약이며, 객실이 아직 정해지지 않았으면 roomNumber와 floor가 비어 있다.
 */
public record InHouseGuestDto(
        String roomNumber,   // 미배정이면 null
        Integer floor,       // 미배정이면 null
        String guestName,
        String reservationId,
        RoomType roomType,
        ReservationStatus status,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int currentStayDay,  // 조회 시작일 기준 몇 박째인지. 시작일 이후에 도착하면 1
        int totalNights,
        int nightsInRange    // 조회 기간 안에서 실제로 묵는 밤 수
) {}
