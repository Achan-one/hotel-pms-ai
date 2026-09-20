package com.hotel.service.dto;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 프론트 데스크 룸 체인지 요청 파라미터 DTO
 */
public record RoomChangeRequest(
        String reservationId,        // 예약 번호
        String targetRoomNumber,     // 이동할 신규 호실 번호
        LocalDate moveDate,          // 이동 일자 (당일 기준 남은 박수 분할)
        String reason                // 변경 사유 (메모용)
) {
    public RoomChangeRequest {
        Objects.requireNonNull(reservationId, "예약 ID는 필수입니다.");
        Objects.requireNonNull(targetRoomNumber, "대상 호실 번호는 필수입니다.");
        Objects.requireNonNull(moveDate, "이동 일자는 필수입니다.");
        reason = (reason != null && !reason.isBlank()) ? reason.trim() : "현장 요청";
    }

    // 편의 팩토리 메서드: 이동 일자를 당일(LocalDate.now())로 간편 지정
    public static RoomChangeRequest of(String reservationId, String targetRoomNumber) {
        return new RoomChangeRequest(reservationId, targetRoomNumber, LocalDate.now(), "현장 요청");
    }
}