package com.hotel.channel;

import com.hotel.channel.dto.ChannelInventorySyncDto;
import com.hotel.channel.onda.OndaChannelAdapter;
import com.hotel.channel.tlx.TlxChannelAdapter;
import com.hotel.domain.QuotaPolicy;
import com.hotel.domain.Reservation;
import com.hotel.domain.RoomType;
import com.hotel.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChannelManagerAdapterTest {

    private RoomRepository roomRepository;
    private QuotaPolicy quotaPolicy;
    private ChannelSyncService syncService;

    private final LocalDate today = LocalDate.of(2026, 9, 20);

    @BeforeEach
    void setUp() {
        roomRepository = new RoomRepository();
        quotaPolicy = new QuotaPolicy(); // 기본 킵: 이그제큐티브 1실, 트윈 2실, 레지덴셜 2실
        syncService = new ChannelSyncService(roomRepository, quotaPolicy);
    }

    @Test
    @DisplayName("[TLX Inbound] TL-Lincoln XML 예약 전문을 정상적으로 PMS Reservation으로 파싱해야 한다")
    void tlx_ParseIncomingReservations_Success() {
        TlxChannelAdapter adapter = new TlxChannelAdapter();
        String xml = """
                <TL_Reservations>
                  <Reservation>
                    <ReservationId>TLX-9001</ReservationId>
                    <GuestName>Yamada Taro</GuestName>
                    <RoomType>SUPERIOR_TWIN</RoomType>
                    <CheckInDate>2026-09-20</CheckInDate>
                    <StayNights>2</StayNights>
                    <SpecialRequest>静かな部屋を希望</SpecialRequest>
                  </Reservation>
                </TL_Reservations>
                """;

        List<Reservation> parsed = adapter.parseIncomingReservations(xml);

        assertEquals(1, parsed.size());
        Reservation r = parsed.get(0);
        assertEquals("TLX-9001", r.getReservationId());
        assertEquals("Yamada Taro", r.getGuestName());
        assertEquals(RoomType.SUPERIOR_TWIN, r.getBookedRoomType());
        assertEquals(2, r.getStayNights());
        assertEquals("静かな部屋を希望", r.getRawRequestText());
    }

    @Test
    @DisplayName("[ONDA Outbound] 킵 수량을 차감한 최종 판매 가능 재고(Sellable)가 ONDA JSON으로 직렬화되어야 한다")
    void onda_SerializeInventory_DeductsHoldQuota() {
        OndaChannelAdapter adapter = new OndaChannelAdapter();

        // 이그제큐티브 더블 총 4실 중 킵 1실 -> 판매 가능 수량은 3실이어야 함
        List<ChannelInventorySyncDto> syncDtos = syncService.calculateDailySellableInventory(today);
        ChannelInventorySyncDto execDto = syncDtos.stream()
                .filter(d -> d.roomType() == RoomType.EXECUTIVE_DOUBLE)
                .findFirst()
                .orElseThrow();

        assertEquals(4, execDto.physicalVacant());
        assertEquals(1, execDto.holdQuota());
        assertEquals(3, execDto.sellableInventory());

        String json = adapter.serializeInventoryUpdate(syncDtos);

        assertTrue(json.contains("\"roomType\": \"EXECUTIVE_DOUBLE\""));
        assertTrue(json.contains("\"sellable\": 3"));
        assertTrue(json.contains("\"price\": 28000"));
    }
    @Test
    @DisplayName("[TLX Inbound 취소] 린칸에서 취소 전문 수신 시 CANCEL 요청으로 식별되어야 한다")
    void tlx_ParseIncomingCancel_Success() {
        TlxChannelAdapter adapter = new TlxChannelAdapter();
        String cancelXml = """
                <TL_Reservations>
                  <Reservation>
                    <ReservationId>TLX-CANCEL-101</ReservationId>
                    <TransactionType>CANCEL</TransactionType>
                  </Reservation>
                </TL_Reservations>
                """;

        var requests = adapter.parseIncomingRequests(cancelXml);

        assertEquals(1, requests.size());
        var req = requests.get(0);
        assertEquals(com.hotel.channel.dto.ChannelReservationRequest.ActionType.CANCEL, req.actionType());
        assertEquals("TLX-CANCEL-101", req.reservationId());
    }
}