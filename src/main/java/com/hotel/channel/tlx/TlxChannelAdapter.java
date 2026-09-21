package com.hotel.channel.tlx;

import com.hotel.channel.ChannelManagerAdapter;
import com.hotel.channel.dto.ChannelInventorySyncDto;
import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.RoomType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 일본 시장 표준 TL-Lincoln(TLX) XML 전문 처리 어댑터
 */
public class TlxChannelAdapter implements ChannelManagerAdapter {

    @Override
    public String getChannelCode() {
        return "TL_LINCOLN";
    }

    @Override
    public List<Reservation> parseIncomingReservations(String xmlPayload) {
        if (xmlPayload == null || xmlPayload.isBlank()) return List.of();

        List<Reservation> reservations = new ArrayList<>();
        // 간단한 정규식 파서로 TLX XML 노드 추출 (<Reservation>...</Reservation>)
        Pattern resPattern = Pattern.compile("<Reservation>(.*?)</Reservation>", Pattern.DOTALL);
        Matcher matcher = resPattern.matcher(xmlPayload);

        while (matcher.find()) {
            String block = matcher.group(1);
            String rsvId = extractTag(block, "ReservationId");
            String guestName = extractTag(block, "GuestName");
            String roomTypeStr = extractTag(block, "RoomType");
            String checkInStr = extractTag(block, "CheckInDate");
            String nightsStr = extractTag(block, "StayNights");
            String note = extractTag(block, "SpecialRequest");

            RoomType roomType;
            try {
                roomType = RoomType.valueOf(roomTypeStr);
            } catch (Exception e) {
                roomType = RoomType.MODERATE_DOUBLE; // 매핑 실패 시 기본 fallback
            }

            LocalDate checkIn = LocalDate.parse(checkInStr);
            int nights = Integer.parseInt(nightsStr);

            reservations.add(new Reservation(
                    rsvId, guestName, roomType, checkIn, nights, note, GuestPreference.empty()
            ));
        }

        return reservations;
    }

    @Override
    public String serializeInventoryUpdate(List<ChannelInventorySyncDto> syncList) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<TL_Lincoln_ARI_Update>\n");
        for (ChannelInventorySyncDto item : syncList) {
            xml.append("  <InventoryItem>\n");
            xml.append("    <TargetDate>").append(item.targetDate()).append("</TargetDate>\n");
            xml.append("    <RoomType>").append(item.roomType().name()).append("</RoomType>\n");
            xml.append("    <SellableStock>").append(item.sellableInventory()).append("</SellableStock>\n");
            xml.append("    <RateAmount currency=\"JPY\">").append(item.rateYen()).append("</RateAmount>\n");
            xml.append("  </InventoryItem>\n");
        }
        xml.append("</TL_Lincoln_ARI_Update>");
        return xml.toString();
    }

    private String extractTag(String source, String tagName) {
        Pattern p = Pattern.compile("<" + tagName + ">(.*?)</" + tagName + ">", Pattern.DOTALL);
        Matcher m = p.matcher(source);
        return m.find() ? m.group(1).trim() : "";
    }
}