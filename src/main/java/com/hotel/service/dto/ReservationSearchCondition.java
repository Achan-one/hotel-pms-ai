package com.hotel.service.dto;

import com.hotel.domain.ReservationStatus;
import com.hotel.domain.RoomType;

import java.time.LocalDate;

/**
 * 프론트 데스크 다조건 복합 검색 DTO
 */
public record ReservationSearchCondition(
        String reservationId,        // 예약 번호 (일치 또는 포함)
        String guestName,            // 투숙객 이름 (대소문자 무시 부분 일치)
        LocalDate checkInDate,       // 체크인 당일 일치 필터
        LocalDate stayingDate,       // 신규: 특정 날짜 체류 중인 투숙객 필터 [checkIn, checkOut)
        Integer stayNights,          // 숙박 박수
        RoomType roomType,           // 예약 객실 타입
        ReservationStatus status,    // 예약 상태
        String assignedRoomNumber    // 배정된 방 번호
) {
    // 7개 인자 레거시 호환 팩토리 생성자
    public ReservationSearchCondition(String reservationId, String guestName, LocalDate checkInDate,
                                      Integer stayNights, RoomType roomType,
                                      ReservationStatus status, String assignedRoomNumber) {
        this(reservationId, guestName, checkInDate, null, stayNights, roomType, status, assignedRoomNumber);
    }

    public static ReservationSearchCondition byGuestName(String guestName) {
        return new ReservationSearchCondition(null, guestName, null, null, null, null, null, null);
    }

    public static ReservationSearchCondition byCheckInDate(LocalDate checkInDate) {
        return new ReservationSearchCondition(null, null, checkInDate, null, null, null, null, null);
    }

    public static ReservationSearchCondition byStayingDate(LocalDate stayingDate) {
        return new ReservationSearchCondition(null, null, null, stayingDate, null, null, null, null);
    }
}