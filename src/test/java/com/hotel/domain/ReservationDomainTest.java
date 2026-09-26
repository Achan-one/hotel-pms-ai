package com.hotel.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReservationDomainTest {

    private final LocalDate today = LocalDate.of(2026, 9, 20);

    @Test
    @DisplayName("[요금 스케줄 기본값] 예약 생성 시 박수만큼 기본 일자별 요금 스케줄이 균등 분할 생성되어야 한다")
    void reservation_CreatesDefaultDailyRateSchedule() {
        Reservation rsv = new Reservation(
                "RSV-TEST-001",
                "John Doe",
                RoomType.SUPERIOR_TWIN,
                today,
                3,
                2,
                "금연실 희망",
                GuestPreference.empty(),
                BookingChannelInfo.direct("DIR-001"),
                BreakfastOption.none(),
                new PaymentLedger(PaymentLedger.PaymentType.PREPAID, 45_000L),
                LocalTime.of(15, 0)
        );

        DailyRateSchedule schedule = rsv.getDailyRateSchedule();
        assertNotNull(schedule);
        assertEquals(3, schedule.getDailyRates().size());
        assertEquals(15_000L, schedule.getRateForDate(today));
        assertEquals(15_000L, schedule.getRateForDate(today.plusDays(1)));
        assertEquals(15_000L, schedule.getRateForDate(today.plusDays(2)));
        assertEquals(45_000L, schedule.calculateTotalRate());
    }

    @Test
    @DisplayName("[요금 스케줄 수정] 운영자가 일자별 1박 객실료를 다르게 수정하면 해당 금액이 반영되어야 한다")
    void reservation_UpdatesDailyRates() {
        Reservation rsv = new Reservation(
                "RSV-TEST-002",
                "Jane Smith",
                RoomType.MODERATE_DOUBLE,
                today,
                2,
                null,
                GuestPreference.empty()
        );

        rsv.updateDailyRates(Map.of(
                today, 12_000L,
                today.plusDays(1), 18_000L
        ));

        DailyRateSchedule schedule = rsv.getDailyRateSchedule();
        assertEquals(12_000L, schedule.getRateForDate(today));
        assertEquals(18_000L, schedule.getRateForDate(today.plusDays(1)));
        assertEquals(30_000L, schedule.calculateTotalRate());
    }

    @Test
    @DisplayName("[체크아웃 정산 방어] 미납 잔액이 남아있으면 체크아웃이 차단되어야 한다")
    void checkOut_BlockedWhenUnsettled() {
        // 현장 결제 고객: 15,000원 결제 예정이나 아직 지불하지 않음 (수납 0원)
        PaymentLedger ledger = new PaymentLedger(PaymentLedger.PaymentType.PAY_ON_ARRIVAL, 15_000L);

        Reservation rsv = new Reservation(
                "RSV-TEST-003",
                "David Miller",
                RoomType.MODERATE_DOUBLE,
                today,
                1,
                1,
                null,
                GuestPreference.empty(),
                BookingChannelInfo.direct("DIR-002"),
                BreakfastOption.none(),
                ledger,
                LocalTime.of(15, 0)
        );

        rsv.assignRoom("0301");
        rsv.checkIn();

        // 사전 검증: 미정산 상태이고 미수금이 15,000원이어야 함
        assertFalse(rsv.getPaymentLedger().isSettled());
        assertEquals(15_000L, rsv.getPaymentLedger().getTotalDue());

        // 1. 미정산 상태에서 체크아웃 시도 시 예외 발생 검증
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> rsv.checkOut());
        assertTrue(ex.getMessage().contains("미정산 금액"));

        // 2. 미수금 전액 수납(settle) 처리
        ledger.settle();
        assertTrue(rsv.getPaymentLedger().isSettled());
        assertEquals(0L, rsv.getPaymentLedger().getTotalDue());

        // 3. 정상 체크아웃 완료 검증
        assertDoesNotThrow(() -> rsv.checkOut());
        assertEquals(ReservationStatus.CHECKED_OUT, rsv.getStatus());
    }
}