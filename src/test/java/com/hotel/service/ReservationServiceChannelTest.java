package com.hotel.service;

import com.hotel.channel.dto.ChannelReservationRequest;
import com.hotel.domain.GuestPreference;
import com.hotel.domain.QuotaPolicy;
import com.hotel.domain.Reservation;
import com.hotel.domain.ReservationStatus;
import com.hotel.domain.Room;
import com.hotel.domain.RoomStatus;
import com.hotel.domain.RoomType;
import com.hotel.domain.StayPeriod;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.TagRepository;
import com.hotel.repository.memory.InMemoryReservationRepository;
import com.hotel.repository.memory.InMemoryRoomRepository;
import com.hotel.repository.memory.InMemoryTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservationServiceChannelTest {

    private ReservationRepository reservationRepository;
    private RoomRepository roomRepository;
    private ReservationService reservationService;

    private final LocalDate today = LocalDate.of(2026, 9, 20);

    @BeforeEach
    void setUp() {
        reservationRepository = new InMemoryReservationRepository();
        roomRepository = new InMemoryRoomRepository();
        TagRepository tagRepository = new InMemoryTagRepository();
        QuotaPolicy quotaPolicy = new QuotaPolicy();

        reservationService = new ReservationService(
                reservationRepository,
                roomRepository,
                null,
                tagRepository,
                quotaPolicy
        );
    }

    @Test
    @DisplayName("[CMS 취소 인입] 취소 전문 수신 시 원장은 CANCELLED로 보존되고 객실 스케줄은 즉시 회수되어야 한다")
    void processChannelRequests_CancelAction_RetainsLedgerAndFreesRoom() {
        // Given: 린칸에서 4박 예약 인입 후 배정 완료
        String rsvId = "TLX-IN-001";
        Reservation booking = new Reservation(
                rsvId, "Yamada Taro", RoomType.SUPERIOR_TWIN, today, 4, "도쿄타워 전망", GuestPreference.empty()
        );
        reservationService.receiveReservations(List.of(booking));
        reservationService.runDailyBatchAssignment(today);

        Reservation assignedGuest = reservationRepository.findById(rsvId).orElseThrow();
        String assignedRoomNumber = assignedGuest.getAssignedRoomNumber();
        assertNotNull(assignedRoomNumber);
        assertEquals(ReservationStatus.ASSIGNED, assignedGuest.getStatus());

        Room assignedRoom = roomRepository.findByRoomNumber(assignedRoomNumber).orElseThrow();
        StayPeriod stayPeriod = new StayPeriod(today, 4);
        assertFalse(assignedRoom.isAvailable(stayPeriod), "배정 직후에는 해당 기간이 점유되어 있어야 함");

        // When: 채널 매니저에서 취소(CANCEL) 전문 웹훅 수신
        ChannelReservationRequest cancelReq = ChannelReservationRequest.cancel(rsvId);
        reservationService.processChannelRequests(List.of(cancelReq));

        // Then 1: 원장 데이터는 삭제되지 않고 CANCELLED 상태로 영구 보존
        Reservation cancelledGuest = reservationRepository.findById(rsvId).orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, cancelledGuest.getStatus());
        assertEquals(1, reservationRepository.count(), "원장에서 행이 삭제되면 안 됨");

        // Then 2: 객실 스케줄이 반납되어 즉시 공실(VACANT) 및 재판매 가능 상태로 환원
        assertTrue(assignedRoom.isAvailable(stayPeriod), "스케줄이 회수되어 해당 기간 예약이 다시 가능해야 함");
        assertEquals(RoomStatus.VACANT, assignedRoom.getStatus(), "하우스키핑 상태가 공실(VACANT)이어야 함");
    }
}