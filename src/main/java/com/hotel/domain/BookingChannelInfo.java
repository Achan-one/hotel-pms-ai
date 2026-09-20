package com.hotel.domain;

import java.util.Objects;

public record BookingChannelInfo(
        ChannelType channelType,     // JALAN, RAKUTEN, AGODA, DIRECT 등
        String channelReservationNo, // OTA 원천 예약 번호 (예: TLX-RAKUTEN-99120)
        String planName              // 플랜명 (예: "【早割30】朝食付スタンダードプラン")
) {
    public BookingChannelInfo {
        Objects.requireNonNull(channelType, "예약 채널 타입은 필수입니다.");
    }

    public static BookingChannelInfo direct(String reservationNo) {
        return new BookingChannelInfo(ChannelType.DIRECT, reservationNo, "호텔 공식 웹/프론트 일반 플랜");
    }

    public boolean isOta() {
        return channelType != ChannelType.DIRECT && channelType != ChannelType.WALK_IN;
    }

    public enum ChannelType {
        DIRECT("호텔 공식"),
        WALK_IN("현장 워크인"),
        JALAN("자란넷 (Jalan)"),
        RAKUTEN("라쿠텐 트래블 (Rakuten)"),
        AGODA("아고다 (Agoda)"),
        BOOKING_COM("부킹닷컴 (Booking.com)"),
        EXPEDIA("익스피디아 (Expedia)"),
        TRIP_COM("트립닷컴 (Trip.com)");

        private final String description;
        ChannelType(String description) { this.description = description; }
        public String getDescription() { return description; }
    }
}