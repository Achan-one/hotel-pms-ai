package com.hotel.channel.dto;

import com.hotel.domain.Reservation;

public record ChannelReservationRequest(
        ActionType actionType,       // BOOKING, CANCEL
        String reservationId,        // 예약 식별자
        Reservation reservation      // BOOKING일 경우 생성된 객체, CANCEL일 경우 최소 식별 정보
) {
    public enum ActionType {
        BOOKING, CANCEL
    }

    public static ChannelReservationRequest booking(Reservation reservation) {
        return new ChannelReservationRequest(ActionType.BOOKING, reservation.getReservationId(), reservation);
    }

    public static ChannelReservationRequest cancel(String reservationId) {
        return new ChannelReservationRequest(ActionType.CANCEL, reservationId, null);
    }
}