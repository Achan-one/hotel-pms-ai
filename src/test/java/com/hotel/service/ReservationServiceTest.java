package com.hotel.service;

import com.hotel.domain.*;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.service.dto.ReservationSearchCondition;
import com.hotel.service.dto.RoomChangeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReservationServiceTest {

    private ReservationRepository reservationRepository;
    private RoomRepository roomRepository;
    private ReservationService reservationService;

    private final LocalDate today = LocalDate.of(2026, 9, 20);

    @BeforeEach
    void setUp() {
        reservationRepository = new ReservationRepository();
        roomRepository = new RoomRepository();

        AiPreferenceParser stubAiParser = new AiPreferenceParser(null, null) {
            @Override
            public Map<String, GuestPreference> parseBatch(List<Reservation> reservations) {
                GuestPreference pref = new GuestPreference(
                        GuestPreference.FloorPref.HIGH,
                        GuestPreference.ElevatorPref.AWAY,
                        GuestPreference.CornerPref.PREFER,
                        true
                );
                return reservations.stream()
                        .collect(java.util.stream.Collectors.toMap(Reservation::getReservationId, r -> pref));
            }
        };

        reservationService = new ReservationService(reservationRepository, roomRepository, stubAiParser);
    }

    @Test
    @DisplayName("[1. 예약 접수] 유효한 예약만 PENDING 상태로 장부에 정상 저장되어야 한다")
    void receiveReservations_Success() {
        Reservation valid1 = new Reservation("RSV-001", "Tanaka", RoomType.MODERATE_DOUBLE, today, 2, "고층 희망", null);
        Reservation valid2 = new Reservation("RSV-002", "Alice", RoomType.SUPERIOR_TWIN, today, 3, "조용한 방", null);
        Reservation invalid = new Reservation("RSV-003", "Bob", RoomType.SUPERIOR_TWIN, today, 32, "초과", null);

        List<Reservation> received = reservationService.receiveReservations(List.of(valid1, valid2, invalid));

        assertEquals(2, received.size());
        assertEquals(2, reservationRepository.count());

        Reservation saved = reservationRepository.findById("RSV-001").orElseThrow();
        assertEquals(ReservationStatus.PENDING, saved.getStatus());
        assertNull(saved.getAssignedRoomNumber());
    }

    @Test
    @DisplayName("[2. 당일 자동 배정] PENDING 예약이 AI 선호도 주입 후 ASSIGNED 상태로 배정 확정되어야 한다")
    void runDailyBatchAssignment_Success() {
        Reservation r1 = new Reservation("RSV-BATCH-01", "Guest1", RoomType.MODERATE_DOUBLE, today, 2, "고층", null);
        Reservation r2 = new Reservation("RSV-BATCH-02", "Guest2", RoomType.SUPERIOR_TWIN, today, 3, "조용한 방", null);
        reservationService.receiveReservations(List.of(r1, r2));

        BatchAssignmentResult result = reservationService.runDailyBatchAssignment(today);

        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());

        Reservation assignedR1 = reservationRepository.findById("RSV-BATCH-01").orElseThrow();
        assertEquals(ReservationStatus.ASSIGNED, assignedR1.getStatus());
        assertNotNull(assignedR1.getAssignedRoomNumber());
        assertTrue(assignedR1.isAssigned());
        assertEquals(GuestPreference.FloorPref.HIGH, assignedR1.getPreference().getFloorPref());
    }

    @Test
    @DisplayName("[3. 체크인 처리] 배정 확정된 예약은 고객 키 수령 시 STAYING(숙박중)으로 전환되어야 한다")
    void processCheckIn_Success() {
        Reservation r1 = new Reservation("RSV-CHECKIN-01", "Tanaka", RoomType.MODERATE_DOUBLE, today, 2, null, null);
        reservationService.receiveReservations(List.of(r1));
        reservationService.runDailyBatchAssignment(today);

        reservationService.processCheckIn("RSV-CHECKIN-01");

        Reservation inHouseGuest = reservationRepository.findById("RSV-CHECKIN-01").orElseThrow();
        assertEquals(ReservationStatus.STAYING, inHouseGuest.getStatus());
        assertTrue(inHouseGuest.getStatus().isInHouse());
    }

    @Test
    @DisplayName("[3-1. 체크인 방어] 미배정(PENDING) 상태의 예약은 체크인을 시도하면 예외가 발생해야 한다")
    void processCheckIn_Unassigned_ThrowsException() {
        Reservation unassigned = new Reservation("RSV-FAIL-01", "FailGuest", RoomType.MODERATE_DOUBLE, today, 1, null, null);
        reservationService.receiveReservations(List.of(unassigned));

        assertThrows(IllegalStateException.class, () -> {
            reservationService.processCheckIn("RSV-FAIL-01");
        }, "미배정 예약은 체크인할 수 없습니다.");
    }

    @Test
    @DisplayName("[4. 룸 체인지] 동일 타입 공실로 이동 성공 시 예약 상태가 ROOM_CHANGED로 갱신되어야 한다")
    void processRoomChange_Success() {
        Reservation r1 = new Reservation("RSV-MOVE-01", "Kim", RoomType.SUPERIOR_TWIN, today, 2, null, null);
        reservationService.receiveReservations(List.of(r1));
        reservationService.runDailyBatchAssignment(today);
        reservationService.processCheckIn("RSV-MOVE-01");

        Reservation beforeMove = reservationRepository.findById("RSV-MOVE-01").orElseThrow();
        String originRoom = beforeMove.getAssignedRoomNumber();

        StayPeriod period = new StayPeriod(today, 2);
        Room targetRoom = roomRepository.findAll().stream()
                .filter(room -> room.getRoomType() == RoomType.SUPERIOR_TWIN)
                .filter(room -> !room.getRoomNumber().equals(originRoom))
                .filter(room -> room.isAvailable(period))
                .findFirst()
                .orElseThrow();

        RoomChangeResult result = reservationService.processRoomChange("RSV-MOVE-01", targetRoom.getRoomNumber());

        assertTrue(result.success());
        Reservation movedReservation = reservationRepository.findById("RSV-MOVE-01").orElseThrow();

        assertEquals(ReservationStatus.ROOM_CHANGED, movedReservation.getStatus());
        assertEquals(targetRoom.getRoomNumber(), movedReservation.getAssignedRoomNumber());
        assertTrue(movedReservation.getStatus().isInHouse());
    }

    @Test
    @DisplayName("[5. 체크아웃 처리] 투숙 중 고객은 프론트 정산 완료 후 정상 퇴실(CHECKED_OUT)되어야 한다")
    void processCheckOut_Success() {
        // Given: 체크인 완료된 손님
        Reservation r1 = new Reservation("RSV-OUT-01", "Lee", RoomType.MODERATE_DOUBLE, today, 1, null, null);
        reservationService.receiveReservations(List.of(r1));
        reservationService.runDailyBatchAssignment(today);
        reservationService.processCheckIn("RSV-OUT-01");

        // When: 실무 정산 처리 후 체크아웃
        r1.getPaymentLedger().settle();
        reservationService.processCheckOut("RSV-OUT-01");

        // Then
        Reservation checkedOut = reservationRepository.findById("RSV-OUT-01").orElseThrow();
        assertEquals(ReservationStatus.CHECKED_OUT, checkedOut.getStatus());
        assertFalse(checkedOut.getStatus().isInHouse());
    }

    @Test
    @DisplayName("[6. 통합 검색] 상태 및 체크인 일자 복합 검색으로 대상 예약을 정확히 필터링해야 한다")
    void searchReservations_Success() {
        Reservation r1 = new Reservation("RSV-SEARCH-01", "Tanaka Kenji", RoomType.MODERATE_DOUBLE, today, 1, null, null);
        Reservation r2 = new Reservation("RSV-SEARCH-02", "Alice Smith", RoomType.SUPERIOR_TWIN, today, 2, null, null);
        reservationService.receiveReservations(List.of(r1, r2));

        reservationService.runDailyBatchAssignment(today);
        reservationService.processCheckIn("RSV-SEARCH-01");

        // 원본 순서: (reservationId, guestName, checkInDate, stayNights, roomType, status, assignedRoomNumber)
        ReservationSearchCondition condition = new ReservationSearchCondition(
                null, null, today, null, null, ReservationStatus.STAYING, null
        );

        List<Reservation> results = reservationService.searchReservations(condition);

        assertEquals(1, results.size());
        assertEquals("RSV-SEARCH-01", results.get(0).getReservationId());
        assertEquals("Tanaka Kenji", results.get(0).getGuestName());
    }
}