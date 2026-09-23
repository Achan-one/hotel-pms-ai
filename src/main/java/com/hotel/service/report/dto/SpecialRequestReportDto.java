package com.hotel.service.report.dto;
import com.hotel.domain.RoomType;
import java.util.Set;

/**
 * 당일 고객 요청사항 및 배정 상태 검증 리포트 DTO
 */
public record SpecialRequestReportDto(
        String reservationId,
        String guestName,
        RoomType roomType,
        String assignedRoomNumber,
        String assignedRoomTags,
        String rawRequestText,
        Set<String> preferredTags,
        Set<String> avoidTags,
        boolean hasHardConstraintFail, // HARD 요청 미충족 경고 여부
        String alertMessage
) {}