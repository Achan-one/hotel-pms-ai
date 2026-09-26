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
    @DisplayName("[오딧 선행 방어] 당일 미체크인 도착 예정자가 남아있으면 나이트 오딧이 즉시 차단되어야 한다")
    void nightAudit_BlockedWhenUncheckedArrivalsRemain() {
        Reservation unchecked = new Reservation(
                "RSV-UNCHECKED-01",
                "Tanaka Kenji",
                RoomType.MODERATE_DOUBLE,
                initialDate,
                2,
                null,
                GuestPreference.empty()
        );
        reservationRepository.save(unchecked);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                nightAuditService.runNightAudit(initialDate));
        assertTrue(ex.getMessage().contains("미체크인 예약이 1건 남아있어"));
    }

    @Test
    @DisplayName("[0박 이월 보존] 1박 미도착 예약은 취소되지 않고 0박(새벽 도착/당일 아웃)으로 객실이 보존되어야 한다")
    void nightAudit_SingleNightRolloversToZeroNights_PreservingRoom() {
        // Given: 9/20 체크인 1박 단박 예약 및 0501호 배정 상태
        Reservation singleNight = new Reservation(
                "RSV-SINGLE-01",
                "Yamada Hanako",
                RoomType.SUPERIOR_TWIN,
                initialDate,
                1,
                null,
                GuestPreference.empty()
        );
        singleNight.assignRoom("0501");
        reservationRepository.save(singleNight);

        Room room = roomRepository.findByRoomNumber("0501").orElseThrow();
        room.bookPeriod(new StayPeriod(initialDate, 1));
        room.setStatus(RoomStatus.ASSIGNED);
        roomRepository.save(room);

        // When: 이월 실행
        int rolledCount = nightAuditService.rolloverUncheckedArrivals(initialDate);
        assertEquals(1, rolledCount);

        // Then: 취소(CANCELLED)되지 않고 PENDING/ASSIGNED 유지, 체크인 9/21, 0박으로 변경 확인
        Reservation updated = reservationRepository.findById("RSV-SINGLE-01").orElseThrow();
        assertNotEquals(ReservationStatus.CANCELLED, updated.getStatus());
        assertEquals(LocalDate.of(2026, 9, 21), updated.getCheckInDate());
        assertEquals(0, updated.getStayNights());
        assertEquals("0501", updated.getAssignedRoomNumber()); // 배정 호실 그대로 유지!

        // 오딧 정상 통과 검증
        NightAuditResult result = assertDoesNotThrow(() -> nightAuditService.runNightAudit(initialDate));
        assertTrue(result.success());
        assertEquals(LocalDate.of(2026, 9, 21), result.newBusinessDate());
    }

    @Test
    @DisplayName("[미도착 이월] 미체크인 2박 예약을 이월하면 체크인이 내일로 미뤄지고 1박으로 차감된다")
    void nightAudit_RolloversUncheckedArrivalsAndCompletes() {
        Reservation uncheckedMultiNight = new Reservation(
                "RSV-ROLLOVER-01",
                "Suzuki Ichiro",
                RoomType.SUPERIOR_TWIN,
                initialDate,
                2,
                null,
                GuestPreference.empty()
        );
        reservationRepository.save(uncheckedMultiNight);

        int rolledCount = nightAuditService.rolloverUncheckedArrivals(initialDate);
        assertEquals(1, rolledCount);

        Reservation updated = reservationRepository.findById("RSV-ROLLOVER-01").orElseThrow();
        assertEquals(LocalDate.of(2026, 9, 21), updated.getCheckInDate());
        assertEquals(1, updated.getStayNights());

        NightAuditResult result = assertDoesNotThrow(() -> nightAuditService.runNightAudit(initialDate));
        assertTrue(result.success());
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
}