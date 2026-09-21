package com.hotel.service.report.dto;

import com.hotel.domain.RoomType;

import java.time.LocalDate;

/**
 * 룸 밸런스 / 객실 인벤토리 정합성 리포트 (Room Balance Reconciliation)
 */
public record RoomBalanceReportDto(
        LocalDate targetDate,
        RoomType roomType,
        String roomTypeName,
        int totalInventory,    // 해당 타입 물리 총 객실 수
        int outOfServiceRooms, // 점검/고장(OOS/OOO)
        int stayoverRooms,     // 기존 연박 재실(In-House)
        int arrivalRooms,      // 당일 도착 예정(Arrivals)
        int holdQuota,         // 호텔 관리자 킵 수량
        int sellableInventory, // 최종 판매 가능 잔여 인벤토리
        boolean isBalanced     // 수식 일치 여부: total == (oos + stay + arrival + sellable + hold조정)
) {}