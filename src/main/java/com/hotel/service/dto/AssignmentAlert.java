package com.hotel.service.dto;

import com.hotel.domain.Reservation;

/**
 * 하드 리퀘스트(필수 제약) 미충족 시 프론트에 알리기 위한 경고 모델
 */
public record AssignmentAlert(
        Reservation reservation,
        String assignedRoomNumber,
        String unfulfilledTag,
        String reason
) {
}