package com.hotel.service.report.dto;

import com.hotel.domain.RoomStatus;
import com.hotel.domain.RoomType;

/**
 * 하우스키핑 청소 지시서 아이템 DTO
 */
public record HousekeepingWorkItemDto(
        String roomNumber,
        int floor,
        RoomType roomType,
        RoomStatus roomStatus,
        CleanPriority cleanPriority, // 청소 우선순위
        String taskType,             // 퇴실청소(D/C), 연박청소(S/C), 공실점검(V/I)
        String memo
) {
    public enum CleanPriority {
        P1_URGENT,   // 퇴실 완료 후 당일 신규 입실 대기 (최우선)
        P2_DEPARTURE,// 일반 퇴실 청소 (OUT)
        P3_STAYOVER, // 재실 연박 청소 (STAY)
        P4_INSPECTION// 장기 공실 점검
    }
}