package com.hotel.service.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record FloorMapResponseDto(
        LocalDate targetDate,
        int totalRooms,
        int occupiedRooms,
        int vacantRooms,
        double occupancyRatePercent,
        Map<Integer, List<RoomMatrixItemDto>> floorRooms
) {
}