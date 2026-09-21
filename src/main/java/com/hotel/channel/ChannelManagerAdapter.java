package com.hotel.channel;

import com.hotel.channel.dto.ChannelInventorySyncDto;
import com.hotel.domain.Reservation;

import java.util.List;

public interface ChannelManagerAdapter {

    /**
     * 채널 매니저 식별 코드 (예: "TL_LINCOLN", "ONDA")
     */
    String getChannelCode();

    /**
     * 채널 매니저에서 인입된 전문(XML/JSON)을 PMS 표준 Reservation 목록으로 변환
     */
    List<Reservation> parseIncomingReservations(String rawPayload);

    /**
     * PMS의 판매 가능 재고/요금(ARI) 목록을 해당 채널 매니저 전송 규격으로 직렬화
     */
    String serializeInventoryUpdate(List<ChannelInventorySyncDto> syncList);
}