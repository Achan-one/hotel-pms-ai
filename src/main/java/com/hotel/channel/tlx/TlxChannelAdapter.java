package com.hotel.channel.tlx;

import com.hotel.channel.ChannelManagerAdapter;
import com.hotel.channel.dto.ChannelInventorySyncDto;
import com.hotel.channel.dto.ChannelReservationRequest;
import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.RoomType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TlxChannelAdapter implements ChannelManagerAdapter {

    @Override
    public String getChannelCode() {
        return "TL_LINCOLN";
    }

    @Override
    public List<ChannelReservationRequest> parseIncomingRequests(String xmlPayload) {
        if (xmlPayload == null || xmlPayload.isBlank()) return List.of();

        List<ChannelReservationRequest> requests = new ArrayList<>();
        Pattern resPattern = Pattern.compile("<Reservation>(.*?)</Reservation>", Pattern.DOTALL);
        Matcher matcher = resPattern.matcher(xmlPayload);

        while (matcher.find()) {
            String block = matcher.group(1);
            String rsvId = extractTag(block, "ReservationId");
            String actionTypeStr = extractTag(block, "TransactionType");
            if (actionTypeStr.isBlank()) {
                actionTypeStr = extractTag(block, "ReservationStatus");
            }

            // 취소 전문인 경우 (원장은 보존하고 상태만 취소할 것이므로 ID만 추출)
            if ("CANCEL".equalsIgnoreCase(actionTypeStr)) {
                requests.add(ChannelReservationRequest.cancel(rsvId));
                continue;
            }

            // 신규 예약 전문인 경우
            String guestName = extractTag(block, "GuestName");
            String roomTypeStr = extractTag(block, "RoomType");
            String checkInStr = extractTag(block, "CheckInDate");
            String nightsStr = extractTag(block, "StayNights");
            String note = extractTag(block, "SpecialRequest");

            RoomType roomType;
            try {
                roomType = RoomType.valueOf(roomTypeStr);
            } catch (Exception e) {
                roomType = RoomType.MODERATE_DOUBLE;
            }

            LocalDate checkIn = LocalDate.parse(checkInStr);
            int nights = Integer.parseInt(nightsStr);

            Reservation rsv = new Reservation(
                    rsvId, guestName, roomType, checkIn, nights, note, GuestPreference.empty()
            );
            requests.add(ChannelReservationRequest.booking(rsv));
        }

        return requests;
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