package com.hotel.service.validator;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.RoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReservationValidatorTest {

    private ReservationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ReservationValidator();
    }

    @Test
    @DisplayName("정상 예약 데이터는 검증을 통과해야 한다")
    void validReservation_Success() {
        Reservation r = new Reservation(
                "RSV-001",
                "Tanaka",
                RoomType.MODERATE_DOUBLE,
                2,
                "Quiet please",
                GuestPreference.empty()
        );
        assertNull(validator.validateSingle(r));
    }

    @Test
    @DisplayName("숙박 일수가 30박을 초과하면 거절 사유가 반환되어야 한다")
    void stayNights_ExceedsLimit_Rejected() {
        Reservation r = new Reservation(
                "RSV-002",
                "Suzuki",
                RoomType.MODERATE_DOUBLE,
                35,
                "",
                GuestPreference.empty()
        );
        String reason = validator.validateSingle(r);
        assertNotNull(reason);
        assertTrue(reason.contains("최대 숙박일수"));
    }

    @Test
    @DisplayName("대량 예약 리스트 내 중복 ID가 존재하면 후순위 중복 건만 격리되어야 한다")
    void batch_DuplicateId_Isolated() {
        List<Reservation> batch = List.of(
                new Reservation("RSV-100", "Guest_A", RoomType.SUPERIOR_TWIN, 1, "", GuestPreference.empty()),
                new Reservation("RSV-100", "Guest_B", RoomType.MODERATE_DOUBLE, 2, "", GuestPreference.empty()), // ID 중복 건
                new Reservation("RSV-101", "Guest_C", RoomType.SUPERIOR_DOUBLE, 3, "", GuestPreference.empty())
        );

        ValidationResult result = validator.validateBatch(batch);

        assertEquals(2, result.getValidReservations().size());
        assertEquals(1, result.getRejectedReservations().size());

        assertEquals("RSV-100", result.getRejectedReservations().getFirst().reservation().getReservationId());
        assertTrue(result.getRejectedReservations().getFirst().reason().contains("중복 인입된 예약 ID"));
    }
}