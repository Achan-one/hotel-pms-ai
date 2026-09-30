package com.hotel.service;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.RoomType;
import com.hotel.repository.memory.InMemoryReservationRepository;
import com.hotel.repository.memory.InMemoryRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FolioTransactionValidationTest {

    private static final String RSV_ID = "RSV-FOLIO-01";

    private InMemoryReservationRepository reservationRepository;
    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationRepository = new InMemoryReservationRepository();
        reservationService = new ReservationService(reservationRepository, new InMemoryRoomRepository(), new AiPreferenceParser());
        reservationRepository.save(new Reservation(
                RSV_ID, "Tanaka", RoomType.SUPERIOR_TWIN,
                LocalDate.of(2026, 9, 20), 1, null, GuestPreference.empty()));
    }

    @Test
    @DisplayName("[원장] 0원 이하 금액은 청구와 수납 모두 거부해야 한다")
    void nonPositiveAmount_IsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                reservationService.addFolioTransaction(RSV_ID, "CHARGE", null, "MINIBAR", null, -1_000_000L, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                reservationService.addFolioTransaction(RSV_ID, "PAYMENT", "CASH", null, null, 0L, null, null));

        assertEquals(0L, reservationRepository.findById(RSV_ID).orElseThrow().getPaymentLedger().getBalance());
    }

    @Test
    @DisplayName("[원장] 알 수 없는 거래 유형은 조용히 무시하지 않고 거부해야 한다")
    void unknownType_IsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                reservationService.addFolioTransaction(RSV_ID, "REFUND", null, null, null, 5_000L, null, null));
    }

    @Test
    @DisplayName("[원장] 정상 청구와 수납은 잔액에 반영되어야 한다")
    void validTransactions_UpdateBalance() {
        reservationService.addFolioTransaction(RSV_ID, "CHARGE", null, "MINIBAR", null, 3_000L, null, null);
        reservationService.addFolioTransaction(RSV_ID, "PAYMENT", "CASH", null, null, 1_000L, null, null);

        assertEquals(2_000L, reservationRepository.findById(RSV_ID).orElseThrow().getPaymentLedger().getBalance());
    }
}
