package com.hotel.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class StayPeriodTest {

    private final LocalDate sep20 = LocalDate.of(2026, 9, 20);
    private final LocalDate sep22 = LocalDate.of(2026, 9, 22);
    private final LocalDate sep25 = LocalDate.of(2026, 9, 25);

    @Test
    @DisplayName("2박 투숙 시 반개구간 [체크인, 체크아웃) 정확성 검증")
    void stayPeriod_DateBoundary_Success() {
        StayPeriod period = new StayPeriod(sep20, 2); // 9/20 ~ 9/22 (2박)

        assertEquals(sep20, period.getCheckInDate());
        assertEquals(sep22, period.getCheckOutDate());
        assertTrue(period.contains(sep20));
        assertTrue(period.contains(LocalDate.of(2026, 9, 21)));
        assertFalse(period.contains(sep22), "체크아웃 당일 정오는 투숙에 포함되지 않아야 합니다.");
    }

    @Test
    @DisplayName("체크아웃 당일 인입되는 다음 예약과는 충돌하지 않음 (반개구간 회전율)")
    void stayPeriod_NoOverlap_OnSameDayCheckoutCheckin() {
        StayPeriod firstGuest = new StayPeriod(sep20, sep22); // 9/20 ~ 9/22
        StayPeriod nextGuest = new StayPeriod(sep22, sep25);  // 9/22 ~ 9/25

        assertFalse(firstGuest.overlaps(nextGuest), "9/22 퇴실과 9/22 입실은 겹치지 않아야 합니다.");
        assertFalse(nextGuest.overlaps(firstGuest));
    }

    @Test
    @DisplayName("하루라도 겹치는 예약 기간은 충돌 감지")
    void stayPeriod_Overlap_Detected() {
        StayPeriod guestA = new StayPeriod(sep20, 3); // 9/20 ~ 9/23
        StayPeriod guestB = new StayPeriod(LocalDate.of(2026, 9, 22), 2); // 9/22 ~ 9/24

        assertTrue(guestA.overlaps(guestB), "9/22 밤이 겹치므로 충돌해야 합니다.");
    }
}