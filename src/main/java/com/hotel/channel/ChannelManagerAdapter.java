package com.hotel.channel;

import com.hotel.channel.dto.ChannelInventorySyncDto;
import com.hotel.channel.dto.ChannelReservationRequest;
import com.hotel.domain.Reservation;

import java.util.List;

public interface ChannelManagerAdapter {

    String getChannelCode();

    /**
     * 채널 매니저에서 인입된 전문(신규 예약 및 취소 포함)을 파싱
     */
    List<ChannelReservationRequest> parseIncomingRequests(String rawPayload);

    /**
     * 레거시 호환: 신규 예약만 추출
     */
    default List<Reservation> parseIncomingReservations(String rawPayload) {
        return parseIncomingRequests(rawPayload).stream()
                .filter(req -> req.actionType() == ChannelReservationRequest.ActionType.BOOKING)
                .map(ChannelReservationRequest::reservation)
                .toList();
    }

    String serializeInventoryUpdate(List<ChannelInventorySyncDto> syncList);
}