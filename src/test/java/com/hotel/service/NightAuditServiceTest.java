package com.hotel.service;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.ReservationStatus;
import com.hotel.domain.Room;
import com.hotel.domain.RoomStatus;
import com.hotel.domain.RoomType;
import com.hotel.domain.StayPeriod;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.memory.InMemoryReservationRepository;
import com.hotel.repository.memory.InMemoryRoomRepository;
import com.hotel.service.dto.NightAuditResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NightAuditServiceTest {

    private ReservationRepository reservationRepository;
    private RoomRepository roomRepository;
    private NightAuditService nightAuditService;

    private final LocalDate businessDate = LocalDate.of(2026, 9, 20);

    @BeforeEach
    void setUp() {
        reservationRepository = new InMemoryReservationRepository();
        roomRepository = new InMemoryRoomRepository();
        nightAuditService = new NightAuditService(reservationRepository, roomRepository);
    }

    @Test
    @DisplayName("[Night Audit] 마감 시 미체크인 예약은 노쇼(CANCELLED) 처리되고 객실 스케줄이 회수되며, 재실 고객은 일일 숙박료가 포스팅되어야 한다")
    void runNightAudit_ProcessesNoShowAndPostsRoomCharge() {
        // Given 1: 미체크인(DUE-IN) 당일 도착 예약 1건 (노쇼 대상)
        String noShowRoomNo = "0301";
        Room room1 = roomRepository.findByRoomNumber(noShowRoomNo).orElseThrow();
        StayPeriod period1 = new StayPeriod(businessDate, 2);
        room1.bookPeriod(period1);
        room1.setStatus(RoomStatus.ASSIGNED);
        roomRepository.save(room1);

        Reservation noShowGuest = new Reservation(
                "RSV-NOSHOW-01", "Tanaka Ken", RoomType.MODERATE_DOUBLE,
                businessDate, 2, "고층", GuestPreference.empty()
        );
        noShowGuest.assignRoom(noShowRoomNo);
        reservationRepository.save(noShowGuest);

        // Given 2: 이미 체크인하여 투숙 중인 인하우스(CHECKED_IN) 고객 1건 (룸차지 대상)
        String inHouseRoomNo = "0501";
        Room room2 = roomRepository.findByRoomNumber(inHouseRoomNo).orElseThrow();
        StayPeriod period2 = new StayPeriod(businessDate, 3);
        room2.bookPeriod(period2);
        room2.setStatus(RoomStatus.OCCUPIED);
        roomRepository.save(room2);

        Reservation inHouseGuest = new Reservation(
                "RSV-INHOUSE-01", "Sato Yui", RoomType.SUPERIOR_TWIN,
                businessDate, 3, "조용한 방", GuestPreference.empty()
        );
        inHouseGuest.assignRoom(inHouseRoomNo);
        inHouseGuest.checkIn();
        reservationRepository.save(inHouseGuest);

        // When: 9월 20일 기준 야간 마감(Night Audit) 가동
        NightAuditResult result = nightAuditService.runNightAudit(businessDate);

        // Then 1: 결과 요약 DTO 검증
        assertTrue(result.success());
        assertEquals(businessDate, result.previousBusinessDate());
        assertEquals(businessDate.plusDays(1), result.newBusinessDate());
        assertEquals(1, result.noShowCount());
        assertEquals(1, result.roomChargePostedCount());
        assertEquals(16_000L, result.totalRoomRevenuePosted(), "SUPERIOR_TWIN 1박 요금 16,000엔 가산 확인");

        // Then 2: 노쇼 고객 상태 전이 및 객실 스케줄 회수 검증
        Reservation processedNoShow = reservationRepository.findById("RSV-NOSHOW-01").orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, processedNoShow.getStatus());
        Room freedRoom = roomRepository.findByRoomNumber(noShowRoomNo).orElseThrow();
        assertTrue(freedRoom.isAvailable(period1), "스케줄이 정상 반납되어 재판매 가능해야 함");

        // Then 3: 인하우스 고객 원장(PaymentLedger) 룸차지 포스팅 검증
        Reservation checkedGuest = reservationRepository.findById("RSV-INHOUSE-01").orElseThrow();
        assertEquals(16_000L, checkedGuest.getPaymentLedger().getTotalCharges());
        assertFalse(checkedGuest.getPaymentLedger().isSettled());
    }
}