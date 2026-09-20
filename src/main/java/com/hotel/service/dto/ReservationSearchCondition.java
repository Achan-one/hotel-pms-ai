package com.hotel.service.dto;

import com.hotel.domain.ReservationStatus;
import com.hotel.domain.RoomType;

import java.time.LocalDate;

/**
 * 프론트 데스크 다조건 복합 검색 DTO (null인 조건은 무시하고 일치하는 조건만 AND 필터링)
 */
public record ReservationSearchCondition(
        String reservationId,        // 예약 번호 (일치 또는 포함)
        String guestName,            // 투숙객 이름 (대소문자 무시 부분 일치)
        LocalDate checkInDate,       // 체크인 일자
        Integer stayNights,          // 숙박 박수
        RoomType roomType,           // 예약 객실 타입
        ReservationStatus status,    // 예약 상태 (PENDING, ASSIGNED, STAYING 등)
        String assignedRoomNumber    // 배정된 방 번호
) {
    // 특정 단일 조건 편의 팩토리 메서드들
    public static ReservationSearchCondition byGuestName(String guestName) {
        return new ReservationSearchCondition(null, guestName, null, null, null, null, null);
    }

    public static ReservationSearchCondition byCheckInDate(LocalDate checkInDate) {
        return new ReservationSearchCondition(null, null, checkInDate, null, null, null, null);
    }

    public static ReservationSearchCondition byStayNights(int stayNights) {
        return new ReservationSearchCondition(null, null, null, stayNights, null, null, null);
    }
}