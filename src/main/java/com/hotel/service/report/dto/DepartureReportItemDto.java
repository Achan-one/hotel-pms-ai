package com.hotel.service.report.dto;

import com.hotel.domain.ReservationStatus;

import java.time.LocalDate;

/**
 * 당일 출발 예정자(Departures List) 리포트 아이템 DTO
 */
public record DepartureReportItemDto(
        String reservationId,
        String guestName,
        String roomNumber,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        ReservationStatus status,
        long balanceDue, // PaymentLedger.getTotalDue() 호환 (long)
        boolean isLateCheckOut
) {}