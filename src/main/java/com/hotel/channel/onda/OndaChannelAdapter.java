package com.hotel.channel.onda;

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
 * 한국 시장 ONDA Hub REST/JSON 웹훅 전문 처리 어댑터
 */
public class OndaChannelAdapter implements ChannelManagerAdapter {

    @Override
    public String getChannelCode() {
        return "ONDA_HUB";
    }

    @Override
    public List<Reservation> parseIncomingReservations(String jsonPayload) {
        if (jsonPayload == null || jsonPayload.isBlank()) return List.of();

        List<Reservation> reservations = new ArrayList<>();
        Pattern itemPattern = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL);
        Matcher matcher = itemPattern.matcher(jsonPayload);

        while (matcher.find()) {
            String block = matcher.group(1);
            if (!block.contains("reservationId")) continue;

            String rsvId = extractJsonValue(block, "reservationId");
            String guestName = extractJsonValue(block, "guestName");
            String roomTypeStr = extractJsonValue(block, "roomType");
            String checkInStr = extractJsonValue(block, "checkInDate");
            String nightsStr = extractJsonValue(block, "stayNights");
            String note = extractJsonValue(block, "specialRequests");

            if (rsvId.isBlank() || checkInStr.isBlank()) continue;

            RoomType roomType;
            try {
                roomType = RoomType.valueOf(roomTypeStr);
            } catch (Exception e) {
                roomType = RoomType.MODERATE_DOUBLE;
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
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"channel\": \"ONDA_HUB\",\n  \"inventoryUpdates\": [\n");
        for (int i = 0; i < syncList.size(); i++) {
            ChannelInventorySyncDto item = syncList.get(i);
            json.append(String.format(
                    "    {\"targetDate\": \"%s\", \"roomType\": \"%s\", \"sellable\": %d, \"price\": %d}%s\n",
                    item.targetDate(), item.roomType().name(), item.sellableInventory(), item.rateYen(),
                    (i < syncList.size() - 1) ? "," : ""
            ));
        }
        json.append("  ]\n}");
        return json.toString();
    }

    private String extractJsonValue(String source, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^,\"}]+)\"?");
        Matcher m = p.matcher(source);
        return m.find() ? m.group(1).trim() : "";
    }
}