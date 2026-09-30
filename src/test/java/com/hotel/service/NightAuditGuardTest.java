package com.hotel.service;

import com.hotel.repository.ReservationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
