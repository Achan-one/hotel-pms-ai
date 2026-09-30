package com.hotel.service;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.RoomType;
import com.hotel.repository.ReservationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class NightAuditGuardTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 20);

    @Autowired
    private NightAuditService nightAuditService;

    @Autowired
    private HotelOperationService hotelOperationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @BeforeEach
    void setUp() {
        reservationRepository.clear();
        hotelOperationService.resetBusinessDate(DAY);
    }

    @AfterEach
    void tearDown() {
        hotelOperationService.resetBusinessDate(DAY);
    }

    @Test
    @DisplayName("[나이트 오딧] 마감이 끝나면 영업일이 하루 넘어가고, 같은 날짜를 다시 실행할 수 없다")
    void secondRunOnSameDateIsRejected() {
        nightAuditService.runNightAudit(DAY);

        assertEquals(DAY.plusDays(1), hotelOperationService.getCurrentBusinessDate());
        assertThrows(IllegalStateException.class, () -> nightAuditService.runNightAudit(DAY));
        assertEquals(DAY.plusDays(1), hotelOperationService.getCurrentBusinessDate());
    }

    @Test
    @DisplayName("[나이트 오딧] 현재 영업일이 아닌 날짜로는 실행할 수 없고 영업일도 움직이지 않는다")
    void nonCurrentBusinessDateIsRejected() {
        assertThrows(IllegalStateException.class, () -> nightAuditService.runNightAudit(DAY.plusDays(3)));
        assertThrows(IllegalStateException.class, () -> nightAuditService.runNightAudit(DAY.minusDays(1)));

        assertEquals(DAY, hotelOperationService.getCurrentBusinessDate());
    }

    @Test
    @DisplayName("[나이트 오딧] 영업일을 과거로 되돌려도 이미 마감한 날짜는 다시 실행할 수 없다")
    void rewindingBusinessDateDoesNotReopenAuditedDay() {
        nightAuditService.runNightAudit(DAY);
        hotelOperationService.setBusinessDate(DAY);

        assertThrows(IllegalStateException.class, () -> nightAuditService.runNightAudit(DAY));
    }

    @Test
    @DisplayName("[미체크인 이월] 현재 영업일이 아닌 날짜는 거부한다")
    void rolloverRequiresCurrentBusinessDate() {
        assertThrows(IllegalArgumentException.class, () -> nightAuditService.rolloverUncheckedArrivals(DAY.plusDays(1)));
    }

    private Reservation checkedInGuest(String id) {
        Reservation r = new Reservation(id, "Tanaka", RoomType.SUPERIOR_TWIN, DAY, 2, null, GuestPreference.empty());
        r.assignRoom("0501");
        r.checkIn();
        reservationRepository.save(r);
        return r;
    }

    @Test
    @DisplayName("[나이트 오딧] 요율 항목이 없는 투숙객이 있으면 마감하지 않고, 요율을 등록한 뒤 다시 실행할 수 있다")
    void missingRateBlocksAuditWithoutClosingTheDay() {
        Reservation guest = checkedInGuest("RSV-AUD-MISS");
        guest.updateDailyRates(Map.of(DAY.plusDays(30), 10_000L));
        reservationRepository.save(guest);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> nightAuditService.runNightAudit(DAY));
        assertTrue(ex.getMessage().contains("RSV-AUD-MISS"));
        assertEquals(DAY, hotelOperationService.getCurrentBusinessDate(), "실패하면 영업일이 넘어가지 않는다");
        assertEquals(0L, reservationRepository.findById("RSV-AUD-MISS").orElseThrow().getPaymentLedger().getTotalCharges());

        Reservation fixed = reservationRepository.findById("RSV-AUD-MISS").orElseThrow();
        fixed.updateDailyRates(Map.of(DAY, 12_000L, DAY.plusDays(1), 12_000L));
        reservationRepository.save(fixed);

        var result = nightAuditService.runNightAudit(DAY);

        assertEquals(1, result.roomChargePostedCount());
        assertEquals(12_000L, result.totalRoomRevenuePosted());
        assertEquals(DAY.plusDays(1), hotelOperationService.getCurrentBusinessDate());
    }

    @Test
    @DisplayName("[나이트 오딧] 요율이 명시적으로 0원인 투숙객은 청구 없이 마감을 통과한다")
    void explicitZeroRateIsAllowed() {
        Reservation guest = checkedInGuest("RSV-AUD-ZERO");
        guest.updateDailyRates(Map.of(DAY, 0L, DAY.plusDays(1), 0L));
        reservationRepository.save(guest);

        var result = nightAuditService.runNightAudit(DAY);

        assertEquals(0, result.roomChargePostedCount());
        assertEquals(DAY.plusDays(1), hotelOperationService.getCurrentBusinessDate());
    }
}
