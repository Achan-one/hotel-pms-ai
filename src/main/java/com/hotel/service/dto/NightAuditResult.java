package com.hotel.service.dto;

import java.time.LocalDate;
import java.util.List;

public record NightAuditResult(
        LocalDate previousBusinessDate,
        LocalDate newBusinessDate,
        int noShowCount,
        List<String> noShowReservationIds,
        int roomChargePostedCount,
        long totalRoomRevenuePosted,
        boolean success,
        String message
) {}