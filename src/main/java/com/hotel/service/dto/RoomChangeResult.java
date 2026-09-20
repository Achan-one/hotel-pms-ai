package com.hotel.service.dto;

import java.time.LocalDate;

public record RoomChangeResult(
        boolean success,
        String reservationId,
        String originRoomNumber,
        String targetRoomNumber,
        LocalDate effectiveDate,
        int remainingNights,
        String message
) {
    // ==========================================
    // 기존 코드 호환용 편의 게터 (fromRoomNumber, toRoomNumber)
    // ==========================================
    public String fromRoomNumber() {
        return originRoomNumber;
    }

    public String toRoomNumber() {
        return targetRoomNumber;
    }

    // ==========================================
    // 팩토리 메서드들
    // ==========================================
    public static RoomChangeResult failure(String reservationId, String message) {
        return new RoomChangeResult(false, reservationId, null, null, null, 0, message);
    }

    // 신규: 잔여 박수 및 적용일 포함 팩토리 메서드
    public static RoomChangeResult success(String reservationId,
                                           String originRoomNumber,
                                           String targetRoomNumber,
                                           LocalDate effectiveDate,
                                           int remainingNights) {
        String msg = String.format("룸 체인지 성공: [%s호 -> %s호] (잔여 %d박 이전)",
                originRoomNumber, targetRoomNumber, remainingNights);
        return new RoomChangeResult(true, reservationId, originRoomNumber, targetRoomNumber, effectiveDate, remainingNights, msg);
    }

    // 기존 호환용: 4개 인자 호출(String, String, String, String) 수용
    public static RoomChangeResult success(String reservationId,
                                           String originRoomNumber,
                                           String targetRoomNumber,
                                           String message) {
        return new RoomChangeResult(true, reservationId, originRoomNumber, targetRoomNumber, LocalDate.now(), 0, message);
    }
}