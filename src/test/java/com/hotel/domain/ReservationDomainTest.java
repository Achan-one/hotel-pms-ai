package com.hotel.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class ReservationDomainTest {

    private final LocalDate today = LocalDate.of(2026, 9, 20);

    @Test
    @DisplayName("[채널 정보] OTA 예약(자란/라쿠텐)과 직영 예약(DIRECT)을 명확히 식별해야 한다")
    void bookingChannel_Detection() {
        BookingChannelInfo jalan = new BookingChannelInfo(
                BookingChannelInfo.ChannelType.JALAN,
                "JALAN-99201",
                "【早割30】スタンダード朝食付き"
        );
        BookingChannelInfo direct = BookingChannelInfo.direct("RSV-DIR-001");

        assertTrue(jalan.isOta());
        assertEquals("자란넷 (Jalan)", jalan.channelType().getDescription());

        assertFalse(direct.isOta());
        assertEquals(BookingChannelInfo.ChannelType.DIRECT, direct.channelType());
    }

    @Test
    @DisplayName("[조식 관리] 체크인(startStaying) 시 조식 포함 플랜은 식권이 자동 발급되어야 한다")
    void breakfast_AutoIssueOnCheckIn() {
        BreakfastOption breakfast = BreakfastOption.included(2);
        PaymentLedger payment = new PaymentLedger(PaymentLedger.PaymentType.PREPAID, 200_000);

        Reservation rsv = new Reservation(
                "RSV-BF-01", "Tanaka", RoomType.MODERATE_DOUBLE,
                today, 3, 2, "고층", GuestPreference.empty(),
                BookingChannelInfo.direct("RSV-BF-01"),
                breakfast, payment, LocalTime.of(15, 0)
        );

        // 3박 * 2인 = 총 6장의 식권 소요
        assertEquals(6, rsv.getBreakfastOption().calculateTotalTickets(rsv.getStayNights()));
        assertFalse(rsv.getBreakfastOption().isTicketsIssued(), "체크인 전에는 식권이 발급되지 않은 상태여야 함");

        // 방 배정 후 체크인 진행
        rsv.assignRoom("0801");
        rsv.startStaying();

        assertTrue(rsv.getBreakfastOption().isTicketsIssued(), "체크인 완료 즉시 식권 발급 완료 상태로 전이되어야 함");
    }

    @Test
    @DisplayName("[정산 방어] 현장 결제 고객이 요금을 정산하지 않은 상태에서는 체크아웃이 차단되어야 한다")
    void checkOut_BlockedWhenUnsettled() {
        // 현장 결제 플랜 150,000원 세팅 (기본 settled = false)
        PaymentLedger payment = new PaymentLedger(PaymentLedger.PaymentType.PAY_ON_ARRIVAL, 150_000);

        Reservation rsv = new Reservation(
                "RSV-PAY-01", "Sato", RoomType.SUPERIOR_TWIN,
                today, 1, 1, null, GuestPreference.empty(),
                null, null, payment, LocalTime.of(16, 0)
        );

        rsv.assignRoom("0502");
        rsv.startStaying();

        // 정산 없이 체크아웃 시도 시 예외 발생 검증
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> rsv.checkOut());
        assertTrue(ex.getMessage().contains("미정산 금액"));

        // 정산 완료 처리 후 체크아웃 재시도 -> 성공 (람다 표현식으로 명시)
        payment.settle();
        assertDoesNotThrow(() -> rsv.checkOut());
        assertEquals(ReservationStatus.CHECKED_OUT, rsv.getStatus());
    }

    @Test
    @DisplayName("[사전 결제 부대비용] 사전 카드 결제 고객도 현장 부대비용(미니바 등) 발생 시 정산 전 체크아웃이 차단되어야 한다")
    void prepaid_BlockedWhenIncidentalAdded() {
        // 사전 결제 180,000원 세팅 (초기 settled = true)
        PaymentLedger payment = new PaymentLedger(PaymentLedger.PaymentType.PREPAID, 180_000);
        assertTrue(payment.isSettled());

        Reservation rsv = new Reservation(
                "RSV-PRE-01", "Alice", RoomType.EXECUTIVE_DOUBLE,
                today, 2, 2, null, GuestPreference.empty(),
                null, null, payment, LocalTime.of(15, 0)
        );

        rsv.assignRoom("1201");
        rsv.startStaying();

        // 투숙 중 미니바 12,000원 청구 발생 -> 미정산 상태로 전환
        payment.addIncidental(12_000);
        assertFalse(payment.isSettled());
        assertEquals(12_000, payment.getTotalDue());

        // 미정산 부대비용으로 인한 체크아웃 차단 검증
        assertThrows(IllegalStateException.class, () -> rsv.checkOut());

        // 프론트 데스크 결제 완료 처리
        payment.settle();
        assertDoesNotThrow(() -> rsv.checkOut());
        assertEquals(ReservationStatus.CHECKED_OUT, rsv.getStatus());
    }

    @Test
    @DisplayName("[레이트 아웃] 지연 퇴실 시간 등록 시 예약 객체에 안전하게 반영되어야 한다")
    void grantLateCheckOut_Success() {
        Reservation rsv = new Reservation(
                "RSV-LATE-01", "Kim", RoomType.MODERATE_DOUBLE,
                today, 1, "조용한 방", GuestPreference.empty()
        );

        assertNull(rsv.getLateCheckOutTime());

        LocalTime lateTime = LocalTime.of(13, 0); // 오후 1시 연장
        rsv.grantLateCheckOut(lateTime);

        assertEquals(lateTime, rsv.getLateCheckOutTime());
    }
}