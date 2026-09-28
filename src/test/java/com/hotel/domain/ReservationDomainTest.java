package com.hotel.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class ReservationDomainTest {

    private Reservation createSampleReservation(PaymentLedger.PaymentType paymentType, long roomRateTotal) {
        PaymentLedger paymentLedger = new PaymentLedger(paymentType, roomRateTotal);
        return new Reservation(
                "RSV-TEST-01",
                "Takahashi Misaki",
                RoomType.SUPERIOR_TWIN,
                LocalDate.now(),
                2,
                1,
                "고층 희망",
                null,
                GuestPreference.empty(),
                TagPreference.empty(),
                BookingChannelInfo.direct("RSV-TEST-01"),
                BreakfastOption.none(),
                paymentLedger,
                LocalTime.of(15, 0)
        );
    }

    @Test
    @DisplayName("[체크아웃 정산 방어] 미납 잔액이 남아있으면 체크아웃이 차단되어야 한다")
    void checkOut_BlockedWhenUnsettled() {
        Reservation reservation = createSampleReservation(PaymentLedger.PaymentType.PAY_ON_ARRIVAL, 60000);

        // 🚀 테스트 환경 보정: 미납 잔액 유도를 위한 청구 전표 명시적 주입
        reservation.getPaymentLedger().addCharge("ROOM_CHARGE", "객실료 청구", 60000);

        reservation.assignRoom("0501");
        reservation.checkIn();

        assertThrows(IllegalStateException.class, () -> {
            reservation.checkOut();
        });
    }

    @Test
    @DisplayName("[결제 원장] 현장 결제 건은 잔액이 0원일 때만 정산 완료 처리된다")
    void paymentLedger_BalanceCalculation() {
        PaymentLedger ledger = new PaymentLedger(PaymentLedger.PaymentType.PAY_ON_ARRIVAL, 0);

        ledger.addCharge("ROOM_CHARGE", "룸차지", 50000);
        assertEquals(50000, ledger.getTotalCharges());
        assertEquals(0, ledger.getTotalPayments());
        assertEquals(50000, ledger.getBalance());
        assertFalse(ledger.isSettled());

        ledger.recordPayment(50000);
        assertEquals(50000, ledger.getTotalPayments());
        assertEquals(0, ledger.getBalance());
        assertTrue(ledger.isSettled());
    }
}