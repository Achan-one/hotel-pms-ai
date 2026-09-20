package com.hotel.service.validator;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.RoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReservationValidatorTest {

    private ReservationValidator validator;
    private final LocalDate today = LocalDate.of(2026, 9, 20);

    @BeforeEach
    void setUp() {
        validator = new ReservationValidator();
    }

    @Test
    @DisplayName("정상적인 단일 예약 검증 성공")
    void validSingleReservation_Success() {
        Reservation res = new Reservation("RES001", "Tanaka", RoomType.MODERATE_DOUBLE, today, 3, "조용한 방 부탁합니다", GuestPreference.empty());
        ValidationResult result = validator.validateSingle(res);

        assertTrue(result.isValid());
        assertEquals("RES001", result.getValidReservation().getReservationId());
    }

    @Test
    @DisplayName("체크인 날짜가 누락된 경우 객체 생성 단계에서 차단된다")
    void checkInDate_Null_Failure() {
        assertThrows(NullPointerException.class, () -> {
            new Reservation("RSV-ERR", "Guest", RoomType.MODERATE_DOUBLE, null, 1, null, null);
        });
    }

    @Test
    @DisplayName("최대 31박 투숙 한도 경계값 검증: 31박 성공, 32박 거절")
    void stayNights_BoundaryTest() {
        Reservation valid31 = new Reservation("RES031", "LongStay", RoomType.RESIDENTIAL_DOUBLE, today, 31, "장기 체류", GuestPreference.empty());
        Reservation invalid32 = new Reservation("RES032", "TooLong", RoomType.RESIDENTIAL_DOUBLE, today, 32, "한도 초과 체류", GuestPreference.empty());

        ValidationResult result31 = validator.validateSingle(valid31);
        ValidationResult result32 = validator.validateSingle(invalid32);

        assertTrue(result31.isValid(), "31박은 정상 통과되어야 합니다.");
        assertFalse(result32.isValid(), "32박은 거절되어야 합니다.");
        assertTrue(result32.getReason().contains("최대 투숙 가능 일수(31박)를 초과했습니다"));
    }

    @Test
    @DisplayName("배치 인입 시 중복 ID는 첫 번째 예약만 유지되고 이후 중복은 격리")
    void batch_DuplicateId_Isolated() {
        Reservation res1 = new Reservation("RES100", "Guest1", RoomType.SUPERIOR_DOUBLE, today, 2, "메모1", GuestPreference.empty());
        Reservation res2 = new Reservation("RES100", "Guest2 (중복)", RoomType.EXECUTIVE_DOUBLE, today, 3, "메모2", GuestPreference.empty());

        List<Reservation> filtered = validator.filterValidReservations(List.of(res1, res2));

        assertEquals(1, filtered.size());
        assertEquals("Guest1", filtered.getFirst().getGuestName());
    }
}