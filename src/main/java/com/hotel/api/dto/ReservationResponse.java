package com.hotel.api.dto;

import com.hotel.domain.BookingChannelInfo;
import com.hotel.domain.BreakfastOption;
import com.hotel.domain.DailyRateSchedule;
import com.hotel.domain.FolioTransaction;
import com.hotel.domain.GuestPreference;
import com.hotel.domain.PaymentLedger;
import com.hotel.domain.Reservation;
import com.hotel.domain.ReservationStatus;
import com.hotel.domain.RoomType;
import com.hotel.domain.TagPreference;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 예약 조회 응답. 필드 이름은 기존 응답과 같게 두고, 외부에 내보내면 안 되는 값만 뺐다.
 * - rawXmlPayload(OTA 전문 원문)는 누구에게도 내려주지 않는다.
 * - internalStaffMemo(내부 인계 메모)는 조회 권한이 정직원 이상일 때만 채운다.
 */
public record ReservationResponse(
        String reservationId,
        String originalGuestName,
        RoomType bookedRoomType,
        LocalDate contractCheckInDate,
        int contractStayNights,
        String rawRequestText,
        String operationalGuestName,
        LocalDate operationalCheckInDate,
        int operationalStayNights,
        String internalStaffMemo,
        String guestName,
        LocalDate checkInDate,
        int stayNights,
        LocalDate checkOutDate,
        String assignedRoomNumber,
        String previousRoomNumber,
        LocalDate actualCheckOutDate,
        ReservationStatus status,
        boolean assigned,
        GuestPreference preference,
        TagPreference tagPreference,
        BookingChannelInfo channelInfo,
        BreakfastOption breakfastOption,
        PaymentLedger paymentLedger,
        LocalTime estimatedArrivalTime,
        LocalTime lateCheckOutTime,
        DailyRateSchedule dailyRateSchedule,
        List<FolioTransaction> transactions
) {

    public static ReservationResponse from(Reservation r, boolean includeStaffMemo) {
        return new ReservationResponse(
                r.getReservationId(),
                r.getOriginalGuestName(),
                r.getBookedRoomType(),
                r.getContractCheckInDate(),
                r.getContractStayNights(),
                r.getRawRequestText(),
                r.getOperationalGuestName(),
                r.getOperationalCheckInDate(),
                r.getOperationalStayNights(),
                includeStaffMemo ? r.getInternalStaffMemo() : null,
                r.getGuestName(),
                r.getCheckInDate(),
                r.getStayNights(),
                r.getCheckOutDate(),
                r.getAssignedRoomNumber(),
                r.getPreviousRoomNumber(),
                r.getActualCheckOutDate(),
                r.getStatus(),
                r.isAssigned(),
                r.getPreference(),
                r.getTagPreference(),
                r.getChannelInfo(),
                r.getBreakfastOption(),
                r.getPaymentLedger(),
                r.getEstimatedArrivalTime(),
                r.getLateCheckOutTime(),
                r.getDailyRateSchedule(),
                r.getTransactions()
        );
    }
}
