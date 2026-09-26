package com.hotel.service;

import com.hotel.domain.*;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.memory.InMemoryReservationRepository;
import com.hotel.repository.memory.InMemoryRoomRepository;
import com.hotel.service.dto.NightAuditResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NightAuditServiceTest {

    private ReservationRepository reservationRepository;
    private RoomRepository roomRepository;
    private HotelOperationService hotelOperationService;
    private NightAuditService nightAuditService;

    private final LocalDate initialDate = LocalDate.of(2026, 9, 20);

    @BeforeEach
    void setUp() {
        reservationRepository = new InMemoryReservationRepository();
        roomRepository = new InMemoryRoomRepository();

        hotelOperationService = new HotelOperationService(null) {
            private LocalDate currentDate = initialDate;

            @Override
            public LocalDate getCurrentBusinessDate() {
                return currentDate;
            }

            @Override
            public LocalDate rolloverToNextDate() {
                currentDate = currentDate.plusDays(1);
                return currentDate;
            }

            @Override
            public void setBusinessDate(LocalDate newDate) {
                this.currentDate = newDate;
            }
        };

        nightAuditService = new NightAuditService(reservationRepository, roomRepository, hotelOperationService);
    }

    @Test
    @DisplayName("[나이트 오딧] 인하우스 고객에게 당일 설정 단가가 정상 포스팅 및 집계되어야 한다")
    void nightAudit_PostsDynamicDailyRate() {
        Reservation inHouseGuest = new Reservation(
                "RSV-AUDIT-01",
                "Yamada Taro",
                RoomType.SUPERIOR_TWIN,
                initialDate,
                2,
                "조용한 방",
                GuestPreference.empty()
        );
        inHouseGuest.assignRoom("0501");
        inHouseGuest.checkIn();

        inHouseGuest.updateDailyRates(Map.of(
                initialDate, 25_000L,
                initialDate.plusDays(1), 30_000L
        ));
        reservationRepository.save(inHouseGuest);

        Room room = roomRepository.findByRoomNumber("0501").orElseThrow();
        room.setStatus(RoomStatus.OCCUPIED);
        roomRepository.save(room);

        NightAuditResult result = nightAuditService.runNightAudit(initialDate);

        assertTrue(result.success());
        assertEquals(1, result.roomChargePostedCount());
        assertEquals(25_000L, result.totalRoomRevenuePosted());
        assertEquals(LocalDate.of(2026, 9, 21), result.newBusinessDate());
    }

    @Test
    @DisplayName("[노쇼 처리] 당일 미도착 상태인 예약은 취소되고 객실이 공실로 환원되어야 한다")
    void nightAudit_CancelsNoShowAndReleasesRoom() {
        Reservation noShowGuest = new Reservation(
                "RSV-NOSHOW-01",
                "Suzuki Ichiro",
                RoomType.MODERATE_DOUBLE,
                initialDate,
                1,
                null,
                GuestPreference.empty()
        );
        noShowGuest.assignRoom("0303");
        reservationRepository.save(noShowGuest);

        Room room = roomRepository.findByRoomNumber("0303").orElseThrow();
        room.bookPeriod(new StayPeriod(initialDate, 1));
        room.setStatus(RoomStatus.ASSIGNED);
        roomRepository.save(room);

        NightAuditResult result = nightAuditService.runNightAudit(initialDate);

        assertEquals(1, result.noShowCount());
        assertTrue(result.noShowReservationIds().contains("RSV-NOSHOW-01"));

        Reservation cancelled = reservationRepository.findById("RSV-NOSHOW-01").orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, cancelled.getStatus());

        Room freedRoom = roomRepository.findByRoomNumber("0303").orElseThrow();
        assertEquals(RoomStatus.VACANT, freedRoom.getStatus());
        assertTrue(freedRoom.isAvailable(new StayPeriod(initialDate, 1)));
    }
}