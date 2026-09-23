package com.hotel.service.dto;

import com.hotel.domain.ReservationStatus;
import com.hotel.domain.RoomType;

import java.time.LocalDate;

/**
 * 프론트 데스크 다조건 복합 검색 DTO (Java Record)
 * - Spring @ModelAttribute 바인딩 충돌을 방지하기 위해 단일 Canonical 생성자만 유지합니다.
 */
public record ReservationSearchCondition(
        String reservationId,        // 예약 번호 (일치 또는 포함)
        String guestName,            // 투숙객 이름 (대소문자 무시 부분 일치)
        LocalDate checkInDate,       // 체크인 당일 일치 필터
        LocalDate stayingDate,       // 특정 날짜 체류 중인 투숙객 필터 [checkIn, checkOut)
        Integer stayNights,          // 숙박 박수
        RoomType roomType,           // 예약 객실 타입
        ReservationStatus status,    // 예약 상태
        String assignedRoomNumber,   // 배정된 방 번호
        String tag                   // 🏷️ 태그 검색 (선호/기피 태그 코드 또는 원문 키워드)
) {
    // 8개 인자 레거시 호환 팩토리 메서드
    public static ReservationSearchCondition of(String reservationId, String guestName, LocalDate checkInDate,
                                                LocalDate stayingDate, Integer stayNights, RoomType roomType,
                                                ReservationStatus status, String assignedRoomNumber) {
        return new ReservationSearchCondition(reservationId, guestName, checkInDate, stayingDate, stayNights, roomType, status, assignedRoomNumber, null);
    }

    // 7개 인자 레거시 호환 팩토리 메서드
    public static ReservationSearchCondition of(String reservationId, String guestName, LocalDate checkInDate,
                                                Integer stayNights, RoomType roomType,
                                                ReservationStatus status, String assignedRoomNumber) {
        return new ReservationSearchCondition(reservationId, guestName, checkInDate, null, stayNights, roomType, status, assignedRoomNumber, null);
    }

    public static ReservationSearchCondition byGuestName(String guestName) {
        return new ReservationSearchCondition(null, guestName, null, null, null, null, null, null, null);
    }

    public static ReservationSearchCondition byCheckInDate(LocalDate checkInDate) {
        return new ReservationSearchCondition(null, null, checkInDate, null, null, null, null, null, null);
    }

    public static ReservationSearchCondition byStayingDate(LocalDate stayingDate) {
        return new ReservationSearchCondition(null, null, null, stayingDate, null, null, null, null, null);
    }

    public static ReservationSearchCondition byTag(String tag) {
        return new ReservationSearchCondition(null, null, null, null, null, null, null, null, tag);
    }
}