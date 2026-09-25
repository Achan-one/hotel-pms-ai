package com.hotel.service.dto;

import com.hotel.domain.ReservationStatus;
import com.hotel.domain.RoomType;

import java.time.LocalDate;

public record ReservationSearchCondition(
        String reservationId,        // 예약 번호 (일치 또는 포함)
        String guestName,            // 투숙객 이름 (대소문자 무시 부분 일치)
        LocalDate checkInDate,       // 체크인 당일 일치 필터
        LocalDate stayingDate,       // 특정 날짜 체류 중인 투숙객 필터 [checkIn, checkOut)
        Integer stayNights,          // 숙박 박수
        RoomType roomType,           // 예약 객실 타입
        ReservationStatus status,    // 예약 상태
        String assignedRoomNumber,   // 배정된 방 번호
        String tag,                  // 🏷️ 태그 검색 (선호/기피 태그 코드 또는 원문 키워드)
        String otaChannel            // 🌐 OTA 채널 검색 (AGODA, BOOKING_COM, EXPEDIA 등)
) {
    // 빈 검색 조건 팩토리 메서드 (전수 검색 시 사용)
    public static ReservationSearchCondition empty() {
        return new ReservationSearchCondition(null, null, null, null, null, null, null, null, null, null);
    }

    // 체류일자 기준 팩토리 메서드
    public static ReservationSearchCondition byStayingDate(LocalDate stayingDate) {
        return new ReservationSearchCondition(null, null, null, stayingDate, null, null, null, null, null, null);
    }

    // 체크인 일자 기준 팩토리 메서드
    public static ReservationSearchCondition byCheckInDate(LocalDate checkInDate) {
        return new ReservationSearchCondition(null, null, checkInDate, null, null, null, null, null, null, null);
    }

    // 고객명 기준 팩토리 메서드
    public static ReservationSearchCondition byGuestName(String guestName) {
        return new ReservationSearchCondition(null, guestName, null, null, null, null, null, null, null, null);
    }

    // 태그 기준 팩토리 메서드
    public static ReservationSearchCondition byTag(String tag) {
        return new ReservationSearchCondition(null, null, null, null, null, null, null, null, tag, null);
    }

    // OTA 채널 기준 팩토리 메서드
    public static ReservationSearchCondition byOtaChannel(String otaChannel) {
        return new ReservationSearchCondition(null, null, null, null, null, null, null, null, null, otaChannel);
    }
}